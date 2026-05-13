package com.jarscan.service;

import com.jarscan.config.JarScanProperties;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.DependencyInfo;
import com.jarscan.dto.DuplicateClassFinding;
import com.jarscan.dto.JavaVersionInfo;
import com.jarscan.dto.ManifestInfo;
import com.jarscan.dto.MavenCoordinates;
import com.jarscan.model.ModuleType;
import com.jarscan.model.Severity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateClassAnalysisServiceTests {

    private final DuplicateClassAnalysisService service = new DuplicateClassAnalysisService(new JarScanProperties(
            "0.1.0-test",
            Files.createTempDirectory("jarscan-tests").toString(),
            Files.createTempDirectory("jarscan-tests-db").resolve("jarscan.db").toString(),
            "dependency-check.sh",
            4,
            10_000_000,
            250,
            500,
            20_000_000,
            60,
            "runtime"
    ));

    DuplicateClassAnalysisServiceTests() throws IOException {
    }

    @Test
    void detectsDuplicateClassesAcrossArtifacts() throws IOException {
        Path a = createJar("a", List.of("com/example/Foo.class", "com/example/OnlyA.class"));
        Path b = createJar("b", List.of("com/example/Foo.class", "com/example/OnlyB.class"));

        List<DuplicateClassFinding> findings = service.analyze(List.of(artifact("a.jar", a), artifact("b.jar", b)), new ArrayList<>());

        assertThat(findings)
                .anySatisfy(finding -> {
                    assertThat(finding.findingType()).isEqualTo("EXACT_DUPLICATE_CLASS");
                    assertThat(finding.symbol()).isEqualTo("com.example.Foo.class");
                    assertThat(finding.artifacts()).hasSize(2);
                });
    }

    @Test
    void detectsSplitPackagesWhenClassesAreDifferent() throws IOException {
        Path a = createJar("a", List.of("org/demo/Alpha.class"));
        Path b = createJar("b", List.of("org/demo/Beta.class"));

        List<DuplicateClassFinding> findings = service.analyze(List.of(artifact("a.jar", a), artifact("b.jar", b)), new ArrayList<>());

        assertThat(findings)
                .anySatisfy(finding -> {
                    assertThat(finding.findingType()).isEqualTo("SPLIT_PACKAGE");
                    assertThat(finding.packageName()).isEqualTo("org.demo");
                });
    }

    private ArtifactAnalysis artifact(String fileName, Path sourcePath) {
        return new ArtifactAnalysis(
                fileName,
                fileName,
                10,
                fileName,
                1,
                false,
                null,
                0,
                new MavenCoordinates("org.example", fileName.replace(".jar", ""), "1.0.0"),
                new JavaVersionInfo(61, 61, "Java 17", false),
                new ManifestInfo(null, null, null, null, null, null, null, null, Map.of()),
                ModuleType.CLASSPATH_JAR,
                Severity.UNKNOWN,
                0,
                List.of(),
                List.of(),
                List.of(),
                Map.of("sourcePath", sourcePath.toString()),
                null
        );
    }

    private Path createJar(String prefix, List<String> classEntries) throws IOException {
        Path jar = Files.createTempFile(prefix, ".jar");
        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String classEntry : classEntries) {
                outputStream.putNextEntry(new JarEntry(classEntry));
                outputStream.write(classHeader(61));
                outputStream.closeEntry();
            }
        }
        return jar;
    }

    private byte[] classHeader(int major) {
        return new byte[]{
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                0x00, 0x00,
                (byte) ((major >> 8) & 0xFF),
                (byte) (major & 0xFF),
        };
    }
}
