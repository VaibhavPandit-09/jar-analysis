package com.jarscan.service;

import com.jarscan.config.JarScanProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MavenResolutionServiceTests {

    private final MavenResolutionService mavenResolutionService = new MavenResolutionService(
            new JarScanProperties(
                    "/tmp/jarscan-tests",
                    "dependency-check.sh",
                    4,
                    10_000_000,
                    60,
                    "runtime"
            )
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
    void buildsDependencyTreeCommandSafely() {
        assertThat(mavenResolutionService.buildDependencyTreeCommand(Path.of("/tmp/project/pom.xml"))).containsExactly(
                "mvn",
                "-f", "/tmp/project/pom.xml",
                "dependency:tree",
                "-DoutputType=text"
        );
    }
}
