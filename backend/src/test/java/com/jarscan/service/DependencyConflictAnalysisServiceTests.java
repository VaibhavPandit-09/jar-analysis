package com.jarscan.service;

import com.jarscan.dto.ConvergenceFinding;
import com.jarscan.dto.DependencyTree;
import com.jarscan.dto.DependencyTreeNode;
import com.jarscan.dto.VersionConflictFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyConflictAnalysisServiceTests {

    private final DependencyConflictAnalysisService service = new DependencyConflictAnalysisService();

    @Test
    void detectsMultipleVersionsAndConvergenceIssues() {
        DependencyTree tree = new DependencyTree("TEXT", List.of(
                node("root", "com.example", "demo", "1.0.0", 0, null, false, null, List.of(
                        node("a", "org.example", "alpha", "2.0.0", 1, "root", false, null, List.of(
                                node("shared-selected", "org.libs", "shared", "3.1.0", 2, "a", false, "[INFO] |  \\- org.libs:shared:jar:3.1.0:runtime", List.of())
                        )),
                        node("b", "org.example", "beta", "2.0.0", 1, "root", false, null, List.of(
                                node("shared-omitted", "org.libs", "shared", "2.5.0", 2, "b", true, "[INFO] |  \\- org.libs:shared:jar:2.5.0:runtime (omitted for conflict with 3.1.0)", List.of())
                        ))
                ))
        ));

        DependencyConflictAnalysisService.DependencyConflictAnalysisResult result = service.analyze(tree);

        assertThat(result.versionConflicts()).hasSize(1);
        VersionConflictFinding conflict = result.versionConflicts().getFirst();
        assertThat(conflict.groupId()).isEqualTo("org.libs");
        assertThat(conflict.artifactId()).isEqualTo("shared");
        assertThat(conflict.resolvedVersion()).isEqualTo("3.1.0");
        assertThat(conflict.requestedVersions()).containsExactly("3.1.0", "2.5.0");
        assertThat(conflict.conflictType()).isEqualTo("NEAREST_WINS_CONFLICT");
        assertThat(conflict.dependencyManagementSnippet()).contains("<artifactId>shared</artifactId>");
        assertThat(conflict.pathsByVersion().get("2.5.0").getFirst()).containsExactly(
                "com.example:demo:1.0.0",
                "org.example:beta:2.0.0",
                "org.libs:shared:2.5.0"
        );

        assertThat(result.convergenceFindings()).hasSize(1);
        ConvergenceFinding convergence = result.convergenceFindings().getFirst();
        assertThat(convergence.selectedVersion()).isEqualTo("3.1.0");
        assertThat(convergence.versionsFound()).containsExactly("3.1.0", "2.5.0");
    }

    @Test
    void capturesDependencyManagementOverrideHints() {
        DependencyTree tree = new DependencyTree("TEXT", List.of(
                node("root", "com.example", "demo", "1.0.0", 0, null, false, null, List.of(
                        node("dep", "org.libs", "managed", "5.0.0", 1, "root", false,
                                "[INFO] +- org.libs:managed:jar:5.0.0:runtime (version managed from 4.8.1)", List.of())
                ))
        ));

        VersionConflictFinding conflict = service.analyze(tree).versionConflicts().getFirst();

        assertThat(conflict.conflictType()).isEqualTo("DEPENDENCY_MANAGEMENT_OVERRIDE");
        assertThat(conflict.requestedVersions()).containsExactly("5.0.0", "4.8.1");
        assertThat(conflict.dependencyManagementSnippet()).contains("<version>5.0.0</version>");
    }

    @Test
    void buildsDependencyManagementSnippet() {
        assertThat(service.dependencyManagementSnippet("org.example", "demo", "1.2.3"))
                .contains("<groupId>org.example</groupId>")
                .contains("<artifactId>demo</artifactId>")
                .contains("<version>1.2.3</version>");
    }

    private DependencyTreeNode node(
            String id,
            String groupId,
            String artifactId,
            String version,
            int depth,
            String parentId,
            boolean omitted,
            String rawLine,
            List<DependencyTreeNode> children
    ) {
        return new DependencyTreeNode(
                id,
                groupId,
                artifactId,
                depth == 0 ? "pom" : "jar",
                null,
                version,
                depth == 0 ? null : "runtime",
                depth,
                parentId,
                children,
                depth == 1,
                depth > 1,
                omitted,
                rawLine != null && rawLine.contains("omitted") ? "omitted for conflict with 3.1.0" : null,
                rawLine != null && rawLine.contains("conflict") ,
                rawLine,
                path(groupId, artifactId, version, parentId, children, depth)
        );
    }

    private List<String> path(String groupId, String artifactId, String version, String parentId, List<DependencyTreeNode> children, int depth) {
        return switch (depth) {
            case 0 -> List.of("com.example:demo:1.0.0");
            case 1 -> List.of("com.example:demo:1.0.0", groupId + ":" + artifactId + ":" + version);
            default -> parentId.equals("a")
                    ? List.of("com.example:demo:1.0.0", "org.example:alpha:2.0.0", groupId + ":" + artifactId + ":" + version)
                    : List.of("com.example:demo:1.0.0", "org.example:beta:2.0.0", groupId + ":" + artifactId + ":" + version);
        };
    }
}
