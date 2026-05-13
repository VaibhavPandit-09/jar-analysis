package com.jarscan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarscan.config.JarScanProperties;
import com.jarscan.maven.MavenDependencyAnalyzeParser;
import com.jarscan.maven.MavenDependencyTreeParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MavenResolutionServiceTests {

    private final MavenResolutionService mavenResolutionService = new MavenResolutionService(
            new JarScanProperties(
                    "0.1.0-test",
                    "/tmp/jarscan-tests",
                    "/tmp/jarscan-tests/jarscan.db",
                    "dependency-check.sh",
                    4,
                    10_000_000,
                    250,
                    500,
                    20_000_000,
                    60,
                    "runtime"
            ),
            new MavenDependencyTreeParser(new ObjectMapper()),
            new MavenDependencyAnalyzeParser()
    );

    @Test
    void buildsCopyDependenciesCommandSafely() {
        Path pom = Path.of("/tmp/project/pom.xml");
        Path output = Path.of("/tmp/project/deps");

        assertThat(mavenResolutionService.buildCopyDependenciesCommand(pom, output, "runtime")).containsExactly(
                "mvn",
                "-f", "/tmp/project/pom.xml",
                "dependency:copy-dependencies",
                "-DoutputDirectory=/tmp/project/deps",
                "-DincludeScope=runtime",
                "-Dmdep.useRepositoryLayout=false",
                "-DskipTests"
        );
    }

    @Test
    void buildsDependencyTreeJsonCommandSafely() {
        assertThat(mavenResolutionService.buildDependencyTreeJsonCommand(
                Path.of("/tmp/project/pom.xml"),
                Path.of("/tmp/project/dependency-tree.json")
        )).containsExactly(
                "mvn",
                "-f", "/tmp/project/pom.xml",
                "dependency:tree",
                "-DoutputType=json",
                "-DoutputFile=/tmp/project/dependency-tree.json"
        );
    }

    @Test
    void buildsDependencyTreeTextCommandSafely() {
        assertThat(mavenResolutionService.buildDependencyTreeTextCommand(
                Path.of("/tmp/project/pom.xml"),
                Path.of("/tmp/project/dependency-tree.txt")
        )).containsExactly(
                "mvn",
                "-f", "/tmp/project/pom.xml",
                "dependency:tree",
                "-DoutputType=text",
                "-DoutputFile=/tmp/project/dependency-tree.txt"
        );
    }

    @Test
    void buildsDependencyAnalyzeCommandSafely() {
        assertThat(mavenResolutionService.buildDependencyAnalyzeCommand(
                Path.of("/tmp/project/pom.xml")
        )).containsExactly(
                "mvn",
                "-f", "/tmp/project/pom.xml",
                "dependency:analyze",
                "-DskipTests"
        );
    }
}
