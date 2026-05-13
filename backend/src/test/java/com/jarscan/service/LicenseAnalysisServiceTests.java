package com.jarscan.service;

import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.JavaVersionInfo;
import com.jarscan.dto.LicenseFinding;
import com.jarscan.dto.ManifestInfo;
import com.jarscan.dto.MavenCoordinates;
import com.jarscan.model.ModuleType;
import com.jarscan.model.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseAnalysisServiceTests {

    private final LicenseAnalysisService service = new LicenseAnalysisService();

    @Test
    void extractsLicenseFromEmbeddedPom() throws IOException {
        Path jar = Files.createTempFile("licensed-", ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("META-INF/maven/com.example/demo/pom.xml"));
            output.write("""
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>demo</artifactId>
                      <version>1.0.0</version>
                      <licenses>
                        <license>
                          <name>Apache License, Version 2.0</name>
                          <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
                        </license>
                      </licenses>
                    </project>
                    """.getBytes());
            output.closeEntry();
        }

        LicenseFinding finding = service.inspectArtifact(artifact(jar), jar);

        assertThat(finding.licenseName()).contains("Apache License, Version 2.0");
        assertThat(finding.licenseUrl()).contains("apache.org");
        assertThat(finding.source()).isEqualTo("EMBEDDED_POM");
        assertThat(finding.confidence()).isEqualTo("HIGH");
        assertThat(finding.category()).isEqualTo("permissive");
    }

    @Test
    void classifiesCommonLicenseFamilies() {
        assertThat(service.classify("MIT License")).isEqualTo("permissive");
        assertThat(service.classify("Eclipse Public License 2.0")).isEqualTo("weak copyleft");
        assertThat(service.classify("GNU Affero General Public License")).isEqualTo("strong copyleft");
        assertThat(service.classify("Unknown thing")).isEqualTo("unknown");
    }

    private ArtifactAnalysis artifact(Path jar) {
        return new ArtifactAnalysis(
                "artifact-1",
                jar.getFileName().toString(),
                10,
                "hash",
                1,
                false,
                null,
                0,
                new MavenCoordinates("com.example", "demo", "1.0.0"),
                new JavaVersionInfo(61, 61, "Java 17", false),
                new ManifestInfo(null, null, null, null, null, null, null, null, Map.of()),
                ModuleType.CLASSPATH_JAR,
                Severity.UNKNOWN,
                0,
                List.of(),
                List.of(),
                List.of(),
                Map.of("sourcePath", jar.toString()),
                null
        );
    }
}
