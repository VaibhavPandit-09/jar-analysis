package com.jarscan.service;

import com.jarscan.dto.DependencyTree;
import com.jarscan.dto.DependencyTreeNode;
import com.jarscan.dto.DependencyUsageFinding;
import com.jarscan.dto.DuplicateClassFinding;
import com.jarscan.dto.VersionConflictFinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DependencySlimmingAdvisorServiceTests {

    private final DependencySlimmingAdvisorService service = new DependencySlimmingAdvisorService();

    @Test
    void generatesExclusionSnippetForApparentlyUnusedTransitiveDependency() {
        DependencySlimmingAdvisorService.DependencySlimmingAdvisorResult result = service.analyze(
                List.of(new DependencyUsageFinding(
                        "org.example",
                        "child",
                        "1.1.0",
                        "APPARENTLY_UNUSED",
                        "HIGH",
                        List.of("No compiled class references mapped to this dependency."),
                        List.of(DependencyUsageAnalysisService.RUNTIME_WARNING),
                        "Review before removal.",
                        List.of(List.of("com.example:demo:1.0.0", "org.example:parent:2.0.0", "org.example:child:1.1.0")),
                        2_048L,
                        0
                )),
                List.of(),
                List.of(),
                dependencyTree(),
                Set.of()
        );

        assertThat(result.opportunities()).singleElement().satisfies(opportunity -> {
            assertThat(opportunity.exclusionSnippet()).contains("<exclusion>");
            assertThat(opportunity.exclusionSnippet()).contains("org.example");
            assertThat(opportunity.warnings()).contains("Review and test before applying exclusions.");
        });
    }

    @Test
    void generatesAwsAdvisorAndDependencySnippet() {
        DependencyUsageFinding bundleFinding = new DependencyUsageFinding(
                "software.amazon.awssdk",
                "bundle",
                "2.25.0",
                "USED",
                "HIGH",
                List.of("Compiled bytecode references matched classes packaged by this dependency."),
                List.of(DependencyUsageAnalysisService.RUNTIME_WARNING),
                "Keep unless narrowing modules.",
                List.of(List.of("com.example:demo:1.0.0", "software.amazon.awssdk:bundle:2.25.0")),
                10_000L,
                0
        );

        DependencySlimmingAdvisorService.DependencySlimmingAdvisorResult result = service.analyze(
                List.of(bundleFinding),
                List.of(new VersionConflictFinding(
                        "org.example",
                        "leaf",
                        "1.2.3",
                        List.of("1.2.3", "0.9.0"),
                        Map.of("1.2.3", List.of(List.of("root", "leaf"))),
                        "NEAREST_WINS_CONFLICT",
                        "HIGH",
                        "Align versions.",
                        "<dependencyManagement />"
                )),
                List.of(new DuplicateClassFinding(
                        "EXACT_DUPLICATE_CLASS",
                        "com.example.Foo.class",
                        "com.example",
                        List.of("a.jar", "b.jar"),
                        "MEDIUM",
                        "Exclude one provider.",
                        "Classpath shadowing warning."
                )),
                dependencyTree(),
                Set.of("s3")
        );

        assertThat(result.awsBundleAdvice()).isNotNull();
        assertThat(result.awsBundleAdvice().suggestedReplacement()).isEqualTo("software.amazon.awssdk:s3");
        assertThat(result.awsBundleAdvice().mavenSnippet()).contains("<artifactId>s3</artifactId>");
        assertThat(result.opportunities()).anyMatch(opportunity -> "VERSION_CONFLICT_REDUCTION".equals(opportunity.opportunityType()));
        assertThat(result.opportunities()).anyMatch(opportunity -> "DUPLICATE_LIBRARY_REDUCTION".equals(opportunity.opportunityType()));
    }

    private DependencyTree dependencyTree() {
        return new DependencyTree(
                "TEXT",
                List.of(new DependencyTreeNode(
                        "root",
                        "com.example",
                        "demo",
                        "pom",
                        null,
                        "1.0.0",
                        null,
                        0,
                        null,
                        List.of(new DependencyTreeNode(
                                "parent",
                                "org.example",
                                "parent",
                                "jar",
                                null,
                                "2.0.0",
                                "runtime",
                                1,
                                "root",
                                List.of(new DependencyTreeNode(
                                        "child",
                                        "org.example",
                                        "child",
                                        "jar",
                                        null,
                                        "1.1.0",
                                        "runtime",
                                        2,
                                        "parent",
                                        List.of(),
                                        false,
                                        true,
                                        false,
                                        null,
                                        false,
                                        null,
                                        List.of("com.example:demo:1.0.0", "org.example:parent:2.0.0", "org.example:child:1.1.0")
                                )),
                                true,
                                false,
                                false,
                                null,
                                false,
                                null,
                                List.of("com.example:demo:1.0.0", "org.example:parent:2.0.0")
                        )),
                        false,
                        false,
                        false,
                        null,
                        false,
                        null,
                        List.of("com.example:demo:1.0.0")
                ))
        );
    }
}
