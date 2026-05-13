package com.jarscan.service;

import com.jarscan.dto.DependencyTree;
import com.jarscan.dto.DependencyTreeNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DependencyPathService {

    public List<List<DependencyTreeNode>> findPaths(DependencyTree dependencyTree, String dependencyKey) {
        if (dependencyTree == null || dependencyTree.roots() == null || dependencyKey == null || dependencyKey.isBlank()) {
            return List.of();
        }
        List<List<DependencyTreeNode>> paths = new ArrayList<>();
        for (DependencyTreeNode root : dependencyTree.roots()) {
            collectPaths(root, dependencyKey, new ArrayList<>(), paths);
        }
        return List.copyOf(paths);
    }

    private void collectPaths(
            DependencyTreeNode node,
            String dependencyKey,
            List<DependencyTreeNode> currentPath,
            List<List<DependencyTreeNode>> paths
    ) {
        currentPath.add(node);
        if (dependencyKey.equals(dependencyKey(node))) {
            paths.add(List.copyOf(currentPath));
        }
        for (DependencyTreeNode child : node.children()) {
            collectPaths(child, dependencyKey, currentPath, paths);
        }
        currentPath.removeLast();
    }

    private String dependencyKey(DependencyTreeNode node) {
        return node.groupId() + ":" + node.artifactId();
    }
}
