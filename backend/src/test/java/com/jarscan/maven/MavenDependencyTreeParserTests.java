package com.jarscan.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarscan.dto.DependencyTree;
import com.jarscan.dto.DependencyTreeNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MavenDependencyTreeParserTests {

    private final MavenDependencyTreeParser parser = new MavenDependencyTreeParser(new ObjectMapper());

    @Test
    void parsesSimpleDependencyTree() {
        DependencyTree tree = parser.parseText("""
                [INFO] com.example:demo:pom:1.0.0
                [INFO] +- org.springframework:spring-core:jar:6.1.0:compile
                [INFO] \\- commons-io:commons-io:jar:2.16.1:runtime
                """);

        assertThat(tree.sourceFormat()).isEqualTo("TEXT");
        assertThat(tree.roots()).hasSize(1);
        DependencyTreeNode root = tree.roots().getFirst();
        assertThat(root.groupId()).isEqualTo("com.example");
        assertThat(root.children()).hasSize(2);
        assertThat(root.children().getFirst().direct()).isTrue();
        assertThat(root.children().getFirst().transitive()).isFalse();
        assertThat(root.children().getFirst().scope()).isEqualTo("compile");
    }

    @Test
    void parsesNestedDependencyTreeAndPathFromRoot() {
        DependencyTree tree = parser.parseText("""
                [INFO] com.example:demo:pom:1.0.0
                [INFO] +- org.springframework:spring-context:jar:6.1.0:compile
                [INFO] |  \\- org.springframework:spring-core:jar:6.1.0:compile
                [INFO] |     \\- org.springframework:spring-jcl:jar:6.1.0:compile
                [INFO] \\- ch.qos.logback:logback-classic:jar:1.5.6:runtime
                """);

        DependencyTreeNode springJcl = tree.roots().getFirst()
                .children().getFirst()
                .children().getFirst()
                .children().getFirst();

        assertThat(springJcl.depth()).isEqualTo(3);
        assertThat(springJcl.transitive()).isTrue();
        assertThat(springJcl.pathFromRoot()).containsExactly(
                "com.example:demo:1.0.0",
                "org.springframework:spring-context:6.1.0",
                "org.springframework:spring-core:6.1.0",
                "org.springframework:spring-jcl:6.1.0"
        );
    }

    @Test
    void parsesScopesAndOmittedConflictLines() {
        DependencyTree tree = parser.parseText("""
                [INFO] com.example:demo:pom:1.0.0
                [INFO] +- com.acme:parent:jar:2.0.0:runtime
                [INFO] |  +- com.acme:shared:jar:1.5.0:runtime (version managed from 1.4.0)
                [INFO] |  \\- com.acme:legacy:jar:1.0.0:test (omitted for duplicate)
                [INFO] \\- com.acme:shared:jar:1.4.0:runtime (omitted for conflict with 1.5.0)
                """);

        DependencyTreeNode parent = tree.roots().getFirst().children().getFirst();
        DependencyTreeNode managed = parent.children().getFirst();
        DependencyTreeNode duplicate = parent.children().get(1);
        DependencyTreeNode conflict = tree.roots().getFirst().children().get(1);

        assertThat(managed.omitted()).isFalse();
        assertThat(managed.scope()).isEqualTo("runtime");
        assertThat(duplicate.omitted()).isTrue();
        assertThat(duplicate.omittedReason()).isEqualTo("omitted for duplicate");
        assertThat(duplicate.conflict()).isFalse();
        assertThat(conflict.omitted()).isTrue();
        assertThat(conflict.conflict()).isTrue();
        assertThat(conflict.omittedReason()).isEqualTo("omitted for conflict with 1.5.0");
    }

    @Test
    void ignoresMalformedLinesButKeepsValidNodes() {
        DependencyTree tree = parser.parseText("""
                [INFO] Scanning for projects...
                [INFO] com.example:demo:pom:1.0.0
                [INFO] this is not a dependency line
                [INFO] +- org.example:alpha:jar:1.0.0:compile
                [INFO] --- maven-dependency-plugin:3.8.1:tree (default-cli) @ demo ---
                [INFO] \\- org.example:beta:jar:2.0.0:runtime
                """);

        assertThat(tree.roots()).hasSize(1);
        assertThat(tree.roots().getFirst().children())
                .extracting(DependencyTreeNode::artifactId)
                .containsExactly("alpha", "beta");
    }

    @Test
    void parsesJsonDependencyTreeWhenAvailable() throws Exception {
        DependencyTree tree = parser.parseJson("""
                [INFO] {
                [INFO]   "groupId": "com.example",
                [INFO]   "artifactId": "demo",
                [INFO]   "version": "1.0.0",
                [INFO]   "type": "pom",
                [INFO]   "children": [
                [INFO]     {
                [INFO]       "groupId": "org.example",
                [INFO]       "artifactId": "alpha",
                [INFO]       "version": "2.0.0",
                [INFO]       "type": "jar",
                [INFO]       "scope": "runtime",
                [INFO]       "children": []
                [INFO]     }
                [INFO]   ]
                [INFO] }
                """);

        assertThat(tree.sourceFormat()).isEqualTo("JSON");
        assertThat(tree.roots()).hasSize(1);
        assertThat(tree.roots().getFirst().children().getFirst().scope()).isEqualTo("runtime");
    }
}
