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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class JarAnalyzerServiceTests {

    private final JarAnalyzerService jarAnalyzerService = new JarAnalyzerService(
            new JarScanProperties(
                    "0.1.0-test",
                    Files.createTempDirectory("jarscan-tests").toString(),
                    Files.createTempDirectory("jarscan-tests-db").resolve("jarscan.db").toString(),
                    "dependency-check.sh",
                    4,
                    10_000_000,
                    500,
                    20_000_000,
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
        assertThat(analysis.packagingInspection()).isNotNull();
        assertThat(analysis.packagingInspection().packagingType()).isEqualTo("SPRING_BOOT_EXECUTABLE_JAR");
    }

    @Test
    void detectsSpringBootFatJarLayout() throws IOException {
        Path jarPath = Files.createTempFile("boot-", ".jar");
        writeSpringBootJar(jarPath);

        ArtifactAnalysis analysis = jarAnalyzerService.analyze(jarPath, Files.createTempDirectory("workspace"), new java.util.ArrayList<>());

        assertThat(analysis.packagingInspection()).isNotNull();
        assertThat(analysis.packagingInspection().packagingType()).isEqualTo("SPRING_BOOT_EXECUTABLE_JAR");
        assertThat(analysis.packagingInspection().applicationClassesLocation()).isEqualTo("BOOT-INF/classes");
        assertThat(analysis.packagingInspection().dependencyLibrariesLocation()).isEqualTo("BOOT-INF/lib");
        assertThat(analysis.packagingInspection().springBootVersion()).isEqualTo("3.4.0");
        assertThat(analysis.packagingInspection().layersIndexPresent()).isTrue();
        assertThat(analysis.packagingInspection().classpathIndexPresent()).isTrue();
    }

    @Test
    void detectsWarStructure() throws IOException {
        Path warPath = Files.createTempFile("sample-", ".war");
        writeWar(warPath);

        ArtifactAnalysis analysis = jarAnalyzerService.analyze(warPath, Files.createTempDirectory("workspace"), new java.util.ArrayList<>());

        assertThat(analysis.packagingInspection()).isNotNull();
        assertThat(analysis.packagingInspection().packagingType()).isEqualTo("WAR");
        assertThat(analysis.packagingInspection().webXmlPresent()).isTrue();
        assertThat(analysis.packagingInspection().applicationClassesLocation()).isEqualTo("WEB-INF/classes");
        assertThat(analysis.packagingInspection().dependencyLibrariesLocation()).isEqualTo("WEB-INF/lib");
        assertThat(analysis.packagingInspection().dependencyCount()).isEqualTo(1);
    }

    @Test
    void detectsEarStructure() throws IOException {
        Path earPath = Files.createTempFile("sample-", ".ear");
        writeEar(earPath);

        ArtifactAnalysis analysis = jarAnalyzerService.analyze(earPath, Files.createTempDirectory("workspace"), new java.util.ArrayList<>());

        assertThat(analysis.packagingInspection()).isNotNull();
        assertThat(analysis.packagingInspection().packagingType()).isEqualTo("EAR");
        assertThat(analysis.packagingInspection().applicationXmlPresent()).isTrue();
        assertThat(analysis.packagingInspection().warModuleCount()).isEqualTo(1);
        assertThat(analysis.packagingInspection().jarModuleCount()).isEqualTo(1);
        assertThat(analysis.packagingInspection().modulePaths()).contains("app.war", "client.jar");
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

    private void writeSpringBootJar(Path jarPath) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Main-Class", "org.springframework.boot.loader.launch.JarLauncher");
        attributes.putValue("Start-Class", "com.example.Main");
        attributes.putValue("Spring-Boot-Version", "3.4.0");

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry("BOOT-INF/classes/com/example/Main.class"));
            output.write(classHeader(61));
            output.closeEntry();

            output.putNextEntry(new JarEntry("BOOT-INF/lib/nested-lib.jar"));
            output.write(buildNestedJarBytes());
            output.closeEntry();

            output.putNextEntry(new JarEntry("BOOT-INF/classpath.idx"));
            output.write("BOOT-INF/lib/nested-lib.jar".getBytes());
            output.closeEntry();

            output.putNextEntry(new JarEntry("BOOT-INF/layers.idx"));
            output.write("- dependencies".getBytes());
            output.closeEntry();
        }
    }

    private void writeWar(Path warPath) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(warPath))) {
            output.putNextEntry(new JarEntry("WEB-INF/classes/com/example/Servlet.class"));
            output.write(classHeader(55));
            output.closeEntry();

            output.putNextEntry(new JarEntry("WEB-INF/web.xml"));
            output.write("<web-app/>".getBytes());
            output.closeEntry();

            output.putNextEntry(new JarEntry("WEB-INF/lib/app-lib.jar"));
            output.write(buildNestedJarBytes());
            output.closeEntry();
        }
    }

    private void writeEar(Path earPath) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(earPath))) {
            output.putNextEntry(new JarEntry("META-INF/application.xml"));
            output.write("<application/>".getBytes());
            output.closeEntry();

            output.putNextEntry(new JarEntry("app.war"));
            output.write(buildSimpleZip("WEB-INF/web.xml", "<web-app/>".getBytes()));
            output.closeEntry();

            output.putNextEntry(new JarEntry("client.jar"));
            output.write(buildNestedJarBytes());
            output.closeEntry();

            output.putNextEntry(new JarEntry("lib/shared.jar"));
            output.write(buildNestedJarBytes());
            output.closeEntry();
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

    private byte[] buildSimpleZip(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(content);
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
