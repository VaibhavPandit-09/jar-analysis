package com.jarscan.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarscan.dto.AnalysisResult;
import com.jarscan.dto.AnalysisSummary;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.DependencyInfo;
import com.jarscan.dto.JavaVersionInfo;
import com.jarscan.dto.ManifestInfo;
import com.jarscan.dto.MavenCoordinates;
import com.jarscan.dto.StoredScanResponse;
import com.jarscan.dto.StoredScanSummaryResponse;
import com.jarscan.dto.VulnerabilityFinding;
import com.jarscan.job.AnalysisJob;
import com.jarscan.model.InputType;
import com.jarscan.model.JobStatus;
import com.jarscan.model.ModuleType;
import com.jarscan.model.Severity;
import com.jarscan.service.AnalysisJobService;
import com.jarscan.service.ScanHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanHistoryPersistenceTests {

    @Autowired
    private ScanHistoryService scanHistoryService;

    @Autowired
    private ScanHistoryRepository scanHistoryRepository;

    @Autowired
    private AnalysisJobService analysisJobService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearScans() {
        jdbcTemplate.execute("DELETE FROM scans");
    }

    @Test
    void persistsCompletedScanAndRoundTripsStoredResult() {
        AnalysisJob job = completedJob("job-persist", "sample.jar", "hash-1");

        scanHistoryService.persistCompletedScan(job);

        PersistedScanRecord stored = scanHistoryRepository.findByJobId("job-persist").orElseThrow();
        assertThat(stored.totalArtifacts()).isEqualTo(1);
        assertThat(stored.totalDependencies()).isEqualTo(3);
        assertThat(stored.highCount()).isEqualTo(1);
        assertThat(stored.result()).isNotNull();
        assertThat(stored.result().artifacts()).hasSize(1);
        assertThat(stored.result().artifacts().getFirst().fileName()).isEqualTo("sample.jar");
    }

    @Test
    void listsStoredScansThroughApi() throws Exception {
        AnalysisJob job = completedJob("job-list", "list.jar", "hash-2");
        scanHistoryService.persistCompletedScan(job);

        mockMvc.perform(get("/api/scans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value("job-list"))
                .andExpect(jsonPath("$[0].inputName").value("list.jar"))
                .andExpect(jsonPath("$[0].totalVulnerabilities").value(2));
    }

    @Test
    void loadsStoredScanById() {
        AnalysisJob job = completedJob("job-load", "load.jar", "hash-3");
        scanHistoryService.persistCompletedScan(job);

        String scanId = scanHistoryRepository.findByJobId("job-load").orElseThrow().id();
        StoredScanResponse response = scanHistoryService.getStoredScan(scanId);

        assertThat(response.summary().scanId()).isEqualTo(scanId);
        assertThat(response.result()).isNotNull();
        assertThat(response.result().jobId()).isEqualTo("job-load");
    }

    @Test
    void deletesStoredScanThroughApi() throws Exception {
        AnalysisJob job = completedJob("job-delete", "delete.jar", "hash-4");
        scanHistoryService.persistCompletedScan(job);
        String scanId = scanHistoryRepository.findByJobId("job-delete").orElseThrow().id();

        mockMvc.perform(delete("/api/scans/{scanId}", scanId))
                .andExpect(status().isOk());

        assertThat(scanHistoryRepository.findById(scanId)).isEmpty();
    }

    @Test
    void updatesNotesAndTagsThroughApi() throws Exception {
        AnalysisJob job = completedJob("job-update", "update.jar", "hash-5");
        scanHistoryService.persistCompletedScan(job);
        String scanId = scanHistoryRepository.findByJobId("job-update").orElseThrow().id();

        mockMvc.perform(patch("/api/scans/{scanId}", scanId)
                        .contentType("application/json")
                        .content("""
                                {"notes":"Keep for regression diff","tags":["release","baseline"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Keep for regression diff"))
                .andExpect(jsonPath("$.tags[0]").value("release"))
                .andExpect(jsonPath("$.tags[1]").value("baseline"));

        StoredScanSummaryResponse updated = scanHistoryService.getStoredScan(scanId).summary();
        assertThat(updated.notes()).isEqualTo("Keep for regression diff");
        assertThat(updated.tags()).containsExactly("release", "baseline");
    }

    @Test
    void fallsBackToPersistedResultForLegacyJobResultLookup() {
        AnalysisJob job = completedJob("job-fallback", "fallback.jar", "hash-6");
        scanHistoryService.persistCompletedScan(job);

        AnalysisResult result = analysisJobService.getResult("job-fallback");

        assertThat(result.jobId()).isEqualTo("job-fallback");
        assertThat(result.summary().totalDependencies()).isEqualTo(3);
    }

    @Test
    void preservesResultJsonRoundTripAndSummaryExtraction() throws Exception {
        AnalysisJob job = completedJob("job-json", "json.jar", "hash-7");
        scanHistoryService.persistCompletedScan(job);

        PersistedScanRecord stored = scanHistoryRepository.findByJobId("job-json").orElseThrow();
        AnalysisResult deserialized = objectMapper.readValue(stored.resultJson(), AnalysisResult.class);

        assertThat(deserialized.summary().totalArtifacts()).isEqualTo(1);
        assertThat(deserialized.summary().totalVulnerabilities()).isEqualTo(2);
        assertThat(deserialized.summary().requiredJavaVersion()).isEqualTo("Java 17");
    }

    @Test
    void comparesScansForAddedRemovedAndUpdatedDependenciesAndVulnerabilities() throws Exception {
        scanHistoryService.persistCompletedScan(buildJobWithSingleArtifact(
                "job-base",
                "base.jar",
                "hash-base",
                "com.example",
                "demo",
                "1.0.0",
                "Java 17",
                List.of(new VulnerabilityFinding(Severity.HIGH, "CVE-2026-0001", 7.5, "pkg:maven/com.example/demo@1.0.0", "1.0.0", null, null, List.of(), "dc"))
        ));
        scanHistoryService.persistCompletedScan(buildJobWithSingleArtifact(
                "job-target",
                "target.jar",
                "hash-target",
                "com.example",
                "demo",
                "2.0.0",
                "Java 21",
                List.of(new VulnerabilityFinding(Severity.CRITICAL, "CVE-2026-0002", 9.8, "pkg:maven/com.example/demo@2.0.0", "2.0.0", null, null, List.of(), "dc"))
        ));

        String baseId = scanHistoryRepository.findByJobId("job-base").orElseThrow().id();
        String targetId = scanHistoryRepository.findByJobId("job-target").orElseThrow().id();

        mockMvc.perform(get("/api/compare")
                        .queryParam("base", baseId)
                        .queryParam("target", targetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryDiff.totalArtifacts.before").value(1))
                .andExpect(jsonPath("$.summaryDiff.totalArtifacts.after").value(1))
                .andExpect(jsonPath("$.summaryDiff.totalVulnerabilities.before").value(1))
                .andExpect(jsonPath("$.summaryDiff.totalVulnerabilities.after").value(1))
                .andExpect(jsonPath("$.dependencyChanges.updatedCount").value(1))
                .andExpect(jsonPath("$.vulnerabilityChanges.newCount").value(1))
                .andExpect(jsonPath("$.vulnerabilityChanges.fixedCount").value(1));
    }

    @Test
    void compareEndpointReturnsNotFoundForMissingScan() throws Exception {
        mockMvc.perform(get("/api/compare")
                        .queryParam("base", "missing-base")
                        .queryParam("target", "missing-target"))
                .andExpect(status().isNotFound());
    }

    @Test
    void compareEndpointHandlesMalformedStoredResultJson() throws Exception {
        String malformedId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                        INSERT INTO scans (
                            id, job_id, input_type, input_name, input_hash, status,
                            started_at, completed_at, duration_ms,
                            total_artifacts, total_dependencies, total_vulnerabilities,
                            critical_count, high_count, medium_count, low_count, info_count, unknown_count,
                            highest_cvss, required_java_version, created_app_version,
                            notes, tags, result_json, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                malformedId, "job-malformed", "ARCHIVE_UPLOAD", "bad.jar", "hash-bad", "COMPLETED",
                "2026-05-12T12:00:00Z", "2026-05-12T12:01:00Z", 60000L,
                1, 1, 1, 0, 1, 0, 0, 0, 0, 5.0, "Java 17", "test",
                null, "[]", "{not-valid-json", "2026-05-12T12:01:00Z", "2026-05-12T12:01:00Z");

        AnalysisJob goodJob = completedJob("job-good", "good.jar", "hash-good");
        scanHistoryService.persistCompletedScan(goodJob);
        String goodId = scanHistoryRepository.findByJobId("job-good").orElseThrow().id();

        mockMvc.perform(get("/api/compare")
                        .queryParam("base", malformedId)
                        .queryParam("target", goodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0]").value("Baseline scan has no readable result JSON."))
                .andExpect(jsonPath("$.dependencyChanges.changes.length()").value(0));
    }

    private AnalysisJob completedJob(String jobId, String inputName, String inputHash) {
        AnalysisJob job = new AnalysisJob(jobId, InputType.ARCHIVE_UPLOAD, tempWorkspace());
        Instant startedAt = Instant.parse("2026-05-12T12:00:00Z");
        Instant completedAt = Instant.parse("2026-05-12T12:01:00Z");

        job.status(JobStatus.COMPLETED);
        job.startedAt(startedAt);
        job.completedAt(completedAt);
        job.inputName(inputName);
        job.inputHash(inputHash);
        job.result(sampleResult(jobId, startedAt, completedAt, inputName));
        return job;
    }

    private AnalysisResult sampleResult(String jobId, Instant startedAt, Instant completedAt, String inputName) {
        ArtifactAnalysis artifact = new ArtifactAnalysis(
                "artifact-1",
                inputName,
                5120,
                "sha256",
                14,
                false,
                null,
                0,
                new MavenCoordinates("com.example", "demo", "1.0.0"),
                new JavaVersionInfo(61, 61, "Java 17", false),
                new ManifestInfo("com.example.Main", "Demo", "1.0.0", "Acme", "JUnit", "17", "com.example.demo", null, Map.of()),
                ModuleType.AUTOMATIC_MODULE,
                Severity.HIGH,
                2,
                List.of(new DependencyInfo("demo.jar", new MavenCoordinates("com.example", "demo", "1.0.0"), "runtime", true, "Java 17", 2)),
                List.of(),
                List.of(),
                Map.of("sourcePath", "/tmp/demo.jar"),
                null
        );

        return new AnalysisResult(
                jobId,
                JobStatus.COMPLETED,
                InputType.ARCHIVE_UPLOAD,
                startedAt,
                completedAt,
                new AnalysisSummary(1, 3, 1, 2, 0, 1, 1, 0, 0, 0, 8.7, "Java 17"),
                List.of(artifact),
                null,
                List.of(),
                List.of(),
                null
        );
    }

    private AnalysisJob buildJobWithSingleArtifact(
            String jobId,
            String inputName,
            String inputHash,
            String groupId,
            String artifactId,
            String version,
            String javaVersion,
            List<VulnerabilityFinding> vulnerabilities
    ) {
        AnalysisJob job = new AnalysisJob(jobId, InputType.ARCHIVE_UPLOAD, tempWorkspace());
        Instant startedAt = Instant.parse("2026-05-12T13:00:00Z");
        Instant completedAt = Instant.parse("2026-05-12T13:02:00Z");
        job.status(JobStatus.COMPLETED);
        job.startedAt(startedAt);
        job.completedAt(completedAt);
        job.inputName(inputName);
        job.inputHash(inputHash);
        ArtifactAnalysis artifact = new ArtifactAnalysis(
                "artifact-" + jobId,
                inputName,
                4096,
                "sha-" + jobId,
                11,
                false,
                null,
                0,
                new MavenCoordinates(groupId, artifactId, version),
                new JavaVersionInfo(null, null, javaVersion, false),
                new ManifestInfo(null, null, null, null, null, null, null, null, Map.of()),
                ModuleType.CLASSPATH_JAR,
                vulnerabilities.isEmpty() ? Severity.UNKNOWN : vulnerabilities.getFirst().severity(),
                vulnerabilities.size(),
                List.of(),
                vulnerabilities,
                List.of(),
                Map.of(),
                null
        );
        job.result(new AnalysisResult(
                jobId,
                JobStatus.COMPLETED,
                InputType.ARCHIVE_UPLOAD,
                startedAt,
                completedAt,
                new AnalysisSummary(1, 1, vulnerabilities.isEmpty() ? 0 : 1, vulnerabilities.size(),
                        countSeverity(vulnerabilities, Severity.CRITICAL),
                        countSeverity(vulnerabilities, Severity.HIGH),
                        countSeverity(vulnerabilities, Severity.MEDIUM),
                        countSeverity(vulnerabilities, Severity.LOW),
                        countSeverity(vulnerabilities, Severity.INFO),
                        countSeverity(vulnerabilities, Severity.UNKNOWN),
                        vulnerabilities.stream().map(VulnerabilityFinding::cvssScore).filter(java.util.Objects::nonNull).max(Double::compareTo).orElse(null),
                        javaVersion),
                List.of(artifact),
                null,
                List.of(),
                List.of(),
                null
        ));
        return job;
    }

    private int countSeverity(List<VulnerabilityFinding> vulnerabilities, Severity severity) {
        return (int) vulnerabilities.stream().filter(finding -> finding.severity() == severity).count();
    }

    private java.nio.file.Path tempWorkspace() {
        try {
            return Files.createTempDirectory("scan-history-test");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
