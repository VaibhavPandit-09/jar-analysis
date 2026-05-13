package com.jarscan.dto;

import java.util.List;

public record DependencyTree(
        String sourceFormat,
        List<DependencyTreeNode> roots
) {
}
