package com.jarscan.dto;

import java.util.List;

public record DependencyTreeNode(
        String id,
        String groupId,
        String artifactId,
        String type,
        String classifier,
        String version,
        String scope,
        int depth,
        String parentId,
        List<DependencyTreeNode> children,
        boolean direct,
        boolean transitive,
        boolean omitted,
        String omittedReason,
        boolean conflict,
        String rawLine,
        List<String> pathFromRoot
) {
}
