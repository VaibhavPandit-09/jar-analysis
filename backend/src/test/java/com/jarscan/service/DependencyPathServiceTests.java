package com.jarscan.service;

import com.jarscan.dto.DependencyTree;
import com.jarscan.dto.DependencyTreeNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyPathServiceTests {

    private final DependencyPathService dependencyPathService = new DependencyPathService();

    @Test
    void returnsSinglePathToDependency() {
        DependencyTree tree = new DependencyTree("TEXT", List.of(
                node("root", "com.example", "demo", "1.0.0", 0, null, List.of(
                        node("parent", "org.example", "parent", "2.0.0", 1, "root", List.of(
                                node("target", "org.example", "target", "3.0.0", 2, "parent", List.of())
                        ))
                ))
        ));

        List<List<DependencyTreeNode>> paths = dependencyPathService.findPaths(tree, "org.example:target");

        assertThat(paths).hasSize(1);
        assertThat(paths.getFirst())
                .extracting(DependencyTreeNode::artifactId)
                .containsExactly("demo", "parent", "target");
    }

    @Test
    void returnsMultiplePathsWhenDependencyAppearsInMoreThanOneBranch() {
        DependencyTree tree = new DependencyTree("TEXT", List.of(
                node("root", "com.example", "demo", "1.0.0", 0, null, List.of(
                        node("a", "org.example", "a", "1.0.0", 1, "root", List.of(
                                node("target-1", "org.example", "target", "3.0.0", 2, "a", List.of())
                        )),
                        node("b", "org.example", "b", "1.0.0", 1, "root", List.of(
                                node("target-2", "org.example", "target", "3.0.0", 2, "b", List.of())
                        ))
                ))
        ));

        List<List<DependencyTreeNode>> paths = dependencyPathService.findPaths(tree, "org.example:target");

        assertThat(paths).hasSize(2);
        assertThat(paths)
                .extracting(path -> path.get(1).artifactId())
                .containsExactly("a", "b");
    }

    private DependencyTreeNode node(
            String id,
            String groupId,
            String artifactId,
            String version,
            int depth,
            String parentId,
            List<DependencyTreeNode> children
    ) {
        return new DependencyTreeNode(
                id,
                groupId,
                artifactId,
                "jar",
                null,
                version,
                depth == 0 ? null : "runtime",
                depth,
                parentId,
                children,
                depth == 1,
                depth > 1,
                false,
                null,
                false,
                null,
                List.of()
        );
    }
}
