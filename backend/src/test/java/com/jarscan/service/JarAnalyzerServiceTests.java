package com.jarscan.service;

import com.jarscan.config.JarScanProperties;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.model.ModuleType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

class JarAnalyzerServiceTests {

    private final JarAnalyzerService jarAnalyzerService = new JarAnalyzerService(
            new JarScanProperties(
                    Files.createTempDirectory("jarscan-tests").toString(),
                    "dependency-check.sh",
                    4,
                    10_000_000,
                    60,
                    "runtime"
            )
    );

    JarAnalyzerServiceTests() throws IOException {
    }

    @Test
    void extractsClassVersionManifestAndCoordinates() throws IOException {
        Path jarPath = Files.createTempFile("sample-", ".jar");
        writeJar(jarPath, false);

        ArtifactAnalysis analysis = jarAnalyzerService.analyze(jarPath, Files.createTempDirectory("workspace"), new java.util.ArrayList<>());

        assertThat(analysis.javaVersion().maxMajor()).isEqualTo(61);
        assertThat(analysis.javaVersion().requiredJava()).isEqualTo("Java 17");
        assertThat(analysis.manifest().mainClass()).isEqualTo("com.example.Main");
        assertThat(analysis.coordinates().groupId()).isEqualTo("com.example");
        assertThat(analysis.coordinates().artifactId()).isEqualTo("demo");
        assertThat(analysis.coordinates().version()).isEqualTo("1.0.0");
        assertThat(analysis.moduleType()).isEqualTo(ModuleType.AUTOMATIC_MODULE);
    }

    @Test
    void analyzesNestedJarsInBootInf() throws IOException {
        Path jarPath = Files.createTempFile("nested-", ".jar");
        writeJar(jarPath, true);

        ArtifactAnalysis analysis = jarAnalyzerService.analyze(jarPath, Files.createTempDirectory("workspace"), new java.util.ArrayList<>());

        assertThat(analysis.fatJar()).isTrue();
        assertThat(analysis.nestedArtifacts()).hasSize(1);
        assertThat(analysis.dependencies()).hasSize(1);
        assertThat(analysis.nestedArtifacts().getFirst().fileName()).isEqualTo("BOOT-INF/lib/nested-lib.jar");
    }

    private void writeJar(Path jarPath, boolean nested) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Main-Class", "com.example.Main");
        attributes.putValue("Automatic-Module-Name", "com.example.demo");

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry("com/example/Main.class"));
            output.write(classHeader(61));
            output.closeEntry();

            Properties properties = new Properties();
            properties.setProperty("groupId", "com.example");
            properties.setProperty("artifactId", "demo");
            properties.setProperty("version", "1.0.0");
            ByteArrayOutputStream pomProperties = new ByteArrayOutputStream();
            properties.store(pomProperties, null);

            output.putNextEntry(new JarEntry("META-INF/maven/com.example/demo/pom.properties"));
            output.write(pomProperties.toByteArray());
            output.closeEntry();

            if (nested) {
                output.putNextEntry(new JarEntry("BOOT-INF/lib/nested-lib.jar"));
                output.write(buildNestedJarBytes());
                output.closeEntry();
            }
        }
    }

    private byte[] buildNestedJarBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream output = new JarOutputStream(bytes)) {
            output.putNextEntry(new JarEntry("nested/Nested.class"));
            output.write(classHeader(55));
            output.closeEntry();
        }
        return bytes.toByteArray();
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
