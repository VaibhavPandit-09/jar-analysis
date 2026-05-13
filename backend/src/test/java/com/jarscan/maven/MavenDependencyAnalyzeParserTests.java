package com.jarscan.maven;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MavenDependencyAnalyzeParserTests {

    private final MavenDependencyAnalyzeParser parser = new MavenDependencyAnalyzeParser();

    @Test
    void parsesDependencyAnalyzeSections() {
        String output = """
                [INFO] Used declared dependencies found:
                [INFO]    org.example:used-lib:jar:1.2.3:compile
                [WARNING] Used undeclared dependencies found:
                [WARNING]    org.example:missing-lib:jar:2.0.0:compile
                [WARNING] Unused declared dependencies found:
                [WARNING]    org.example:unused-lib:jar:3.1.0:runtime
                """;

        MavenDependencyAnalyzeResult result = parser.parse(output);

        assertThat(result.usedDeclaredDependencies()).singleElement().satisfies(entry -> {
            assertThat(entry.groupId()).isEqualTo("org.example");
            assertThat(entry.artifactId()).isEqualTo("used-lib");
            assertThat(entry.version()).isEqualTo("1.2.3");
            assertThat(entry.scope()).isEqualTo("compile");
        });
        assertThat(result.usedUndeclaredDependencies()).singleElement().satisfies(entry -> {
            assertThat(entry.artifactId()).isEqualTo("missing-lib");
            assertThat(entry.version()).isEqualTo("2.0.0");
        });
        assertThat(result.unusedDeclaredDependencies()).singleElement().satisfies(entry -> {
            assertThat(entry.artifactId()).isEqualTo("unused-lib");
            assertThat(entry.scope()).isEqualTo("runtime");
        });
    }
}
