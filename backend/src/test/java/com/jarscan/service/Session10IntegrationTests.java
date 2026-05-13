package com.jarscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarscan.dto.AnalysisResult;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.CreateSuppressionRequest;
import com.jarscan.dto.DependencyUsageFinding;
import com.jarscan.dto.DependencyTree;
import com.jarscan.dto.DependencyTreeNode;
import com.jarscan.dto.JavaVersionInfo;
import com.jarscan.dto.LicenseFinding;
import com.jarscan.dto.ManifestInfo;
import com.jarscan.dto.MavenCoordinates;
import com.jarscan.dto.StoredScanResponse;
import com.jarscan.dto.StoredScanSummaryResponse;
import com.jarscan.dto.VersionConflictFinding;
import com.jarscan.dto.VulnerabilityFinding;
import com.jarscan.model.InputType;
import com.jarscan.model.ModuleType;
import com.jarscan.model.Severity;
import com.jarscan.model.SuppressionType;
import com.jarscan.util.AnalysisSummaryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class Session10IntegrationTests {

    private static final Path TEST_DIR;

    static {
        try {
            TEST_DIR = Files.createTempDirectory("jarscan-session10-tests");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jarscan.data-dir", () -> TEST_DIR.toString());
        registry.add("jarscan.db-path", () -> TEST_DIR.resolve("session10.db").toString());
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SuppressionService suppressionService;

    @Autowired
    private ScanResultViewService scanResultViewService;

    @Autowired
    private PolicyService policyService;

    @Autowired
    private SbomService sbomService;

    @Autowired
    private ScanHistoryService scanHistoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        jdbcTemplate.execute("DELETE FROM suppressions");
        jdbcTemplate.execute("DELETE FROM scans");
        jdbcTemplate.execute("DELETE FROM policies");
        policyService.listPolicies();
    }

    @Test
    void appliesPersistedSuppressionsAndReevaluatesPolicies() {
        AnalysisResult raw = sampleResult();

        AnalysisResult baseline = scanResultViewService.decorate(raw);
        assertThat(baseline.artifacts().getFirst().vulnerabilities().getFirst().suppressed()).isFalse();
        assertThat(baseline.policyEvaluation()).isNotNull();
        assertThat(baseline.policyEvaluation().failedCount()).isGreaterThanOrEqualTo(1);

        suppressionService.createSuppression(new CreateSuppressionRequest(
                SuppressionType.VULNERABILITY,
                "org.example",
                "demo",
                "1.0.0",
                "CVE-2026-4242",
                "Accepted temporarily while upstream patch is scheduled",
                null,
                true
        ));
        suppressionService.createSuppression(new CreateSuppressionRequest(
                SuppressionType.LICENSE,
                "org.example",
                "demo",
                "1.0.0",
                null,
                "Accepted for this review while replacement options are evaluated",
                null,
                true
        ));

        AnalysisResult decorated = scanResultViewService.decorate(raw);
        VulnerabilityFinding vulnerability = decorated.artifacts().getFirst().vulnerabilities().getFirst();
        assertThat(vulnerability.suppressed()).isTrue();
        assertThat(vulnerability.suppressionReason()).contains("Accepted temporarily");
        assertThat(decorated.policyEvaluation().failedCount()).isZero();
        assertThat(decorated.summary().overallPolicyStatus()).isEqualTo("WARNING");
    }

    @Test
    void importsCycloneDxSbomAndPersistsAsStoredScan() throws Exception {
        MockMultipartFile sbom = new MockMultipartFile(
                "file",
                "demo.cdx.json",
                "application/json",
                """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.5",
                  "metadata": {
                    "component": {
                      "bom-ref": "com.example:demo:1.0.0",
                      "group": "com.example",
                      "name": "demo",
                      "version": "1.0.0"
                    }
                  },
                  "components": [
                    {
                      "bom-ref": "org.example:leaf:1.2.3",
                      "type": "library",
                      "group": "org.example",
                      "name": "leaf",
                      "version": "1.2.3",
                      "licenses": [
                        { "license": { "name": "Apache-2.0", "url": "https://www.apache.org/licenses/LICENSE-2.0" } }
                      ]
                    }
                  ],
                  "dependencies": [
                    {
                      "ref": "com.example:demo:1.0.0",
                      "dependsOn": ["org.example:leaf:1.2.3"]
                    }
                  ]
                }
                """.getBytes()
        );

        AnalysisResult imported = sbomService.importCycloneDx(sbom);
        assertThat(imported.inputType()).isEqualTo(InputType.SBOM);
        assertThat(imported.dependencyTree()).isNotNull();
        assertThat(imported.licenses()).isNotEmpty();

        StoredScanSummaryResponse stored = scanHistoryService.persistImportedScan(imported, InputType.SBOM, "demo.cdx.json", "hash");
        StoredScanResponse reopened = scanHistoryService.getStoredScan(stored.scanId());
        assertThat(reopened.summary().inputType()).isEqualTo(InputType.SBOM);
        assertThat(reopened.result()).isNotNull();
        assertThat(reopened.result().licenses()).extracting(LicenseFinding::licenseName).contains("Apache-2.0");
    }

    @Test
    void exportsCycloneDxJsonFromAnalysisResult() throws Exception {
        byte[] exported = sbomService.exportCycloneDx(sampleResult());
        JsonNode root = objectMapper.readTree(exported);

        assertThat(root.path("bomFormat").asText()).isEqualTo("CycloneDX");
        assertThat(root.path("components").isArray()).isTrue();
        assertThat(root.path("components")).hasSizeGreaterThanOrEqualTo(1);
        assertThat(root.path("dependencies").isArray()).isTrue();
    }

    private AnalysisResult sampleResult() {
        Instant now = Instant.parse("2026-05-13T12:00:00Z");
        ArtifactAnalysis artifact = new ArtifactAnalysis(
                "artifact-1",
                "demo-1.0.0.jar",
                2048,
                "hash",
                4,
                false,
                null,
                0,
                new MavenCoordinates("org.example", "demo", "1.0.0"),
                new JavaVersionInfo(61, 61, "Java 17", false),
                new ManifestInfo(null, null, "1.0.0", null, null, null, null, null, Map.of()),
                ModuleType.CLASSPATH_JAR,
                Severity.CRITICAL,
                1,
                List.of(),
                List.of(new VulnerabilityFinding(
                        Severity.CRITICAL,
                        "CVE-2026-4242",
                        9.8,
                        "pkg:maven/org.example/demo@1.0.0",
                        "1.0.0",
                        null,
                        "Example critical vulnerability",
                        List.of(),
                        "dependency-check",
                        false,
                        null,
                        null
                )),
                List.of(),
                Map.of(),
                null
        );

        LicenseFinding license = new LicenseFinding(
                "org.example",
                "demo",
                "1.0.0",
                "GPL-3.0",
                null,
                "embedded-pom",
                "HIGH",
                "strong copyleft",
                List.of("Strong copyleft license detected."),
                false,
                null,
                null
        );

        DependencyUsageFinding usage = new DependencyUsageFinding(
                "org.example",
                "demo",
                "1.0.0",
                "APPARENTLY_UNUSED",
                "HIGH",
                List.of("No compiled class references mapped to this dependency."),
                List.of("Review before removing."),
                "Review before removal",
                List.of(List.of("com.example:app:1.0.0", "org.example:demo:1.0.0")),
                2048L,
                1,
                false,
                null,
                null
        );

        VersionConflictFinding conflict = new VersionConflictFinding(
                "org.example",
                "demo",
                "1.0.0",
                List.of("1.0.0", "0.9.0"),
                Map.of("1.0.0", List.of(List.of("root", "org.example:demo:1.0.0"))),
                "NEAREST_WINS_CONFLICT",
                "HIGH",
                "Pin the selected version.",
                "<dependencyManagement />",
                false,
                null,
                null
        );

        DependencyTree tree = new DependencyTree(
                "TEXT",
                List.of(new DependencyTreeNode(
                        "root",
                        "com.example",
                        "app",
                        "pom",
                        null,
                        "1.0.0",
                        null,
                        0,
                        null,
                        List.of(new DependencyTreeNode(
                                "demo",
                                "org.example",
                                "demo",
                                "jar",
                                null,
                                "1.0.0",
                                "runtime",
                                1,
                                "root",
                                List.of(),
                                true,
                                false,
                                false,
                                null,
                                false,
                                null,
                                List.of("com.example:app:1.0.0", "org.example:demo:1.0.0")
                        )),
                        true,
                        false,
                        false,
                        null,
                        false,
                        null,
                        List.of("com.example:app:1.0.0")
                ))
        );

        return new AnalysisResult(
                "job-session10",
                com.jarscan.model.JobStatus.COMPLETED,
                InputType.PROJECT_ZIP,
                now,
                now,
                AnalysisSummaryFactory.create(
                        InputType.PROJECT_ZIP,
                        List.of(artifact),
                        List.of(conflict),
                        List.of(),
                        List.of(),
                        List.of(license),
                        List.of(usage),
                        List.of(),
                        null,
                        null
                ),
                List.of(artifact),
                tree,
                List.of(conflict),
                List.of(),
                List.of(),
                List.of(license),
                List.of(usage),
                List.of(),
                null,
                null,
                null,
                List.of(),
                List.of(),
                null
        );
    }
}
