package com.jarscan.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectStructureDetectorTests {

    private final ProjectStructureDetector detector = new ProjectStructureDetector();

    @Test
    void detectsProjectStructureAndSelectsBestEffortRootPom() throws IOException {
        Path root = Files.createTempDirectory("project-structure");
        Files.createDirectories(root.resolve("module-a/target/classes/com/example"));
        Files.createDirectories(root.resolve("module-a/src/main/resources"));
        Files.writeString(root.resolve("pom.xml"), """
                <project><modelVersion>4.0.0</modelVersion><artifactId>demo-project</artifactId><packaging>pom</packaging><modules><module>module-a</module></modules></project>
                """);
        Files.writeString(root.resolve("module-a/pom.xml"), """
                <project><modelVersion>4.0.0</modelVersion><artifactId>module-a</artifactId></project>
                """);
        Files.write(root.resolve("module-a/target/classes/com/example/App.class"), classHeader(61));
        Files.writeString(root.resolve("module-a/src/main/resources/application.properties"), "spring.application.name=demo");
        Files.createDirectories(root.resolve("module-a/META-INF/services"));
        Files.writeString(root.resolve("module-a/META-INF/services/java.sql.Driver"), "com.example.Driver");

        var warnings = new ArrayList<String>();
        ProjectStructureDetector.ProjectStructureDetection detection = detector.detect(root, "demo-project.zip", warnings);

        assertThat(detection.rootPom()).isEqualTo(root.resolve("pom.xml"));
        assertThat(detection.summary().pomCount()).isEqualTo(2);
        assertThat(detection.summary().compiledClassDirectoryCount()).isEqualTo(1);
        assertThat(detection.summary().resourceFiles()).contains("module-a/src/main/resources/application.properties");
        assertThat(detection.summary().serviceLoaderFiles()).contains("module-a/META-INF/services/java.sql.Driver");
        assertThat(detection.summary().compiledClassesJavaVersion().requiredJava()).isEqualTo("Java 17");
        assertThat(warnings).isEmpty();
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
