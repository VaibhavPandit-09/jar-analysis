package com.jarscan.maven;

import com.jarscan.dto.DependencyTree;

import java.nio.file.Path;
import java.util.List;

public record MavenResolutionResult(
        List<Path> resolvedArtifacts,
        DependencyTree dependencyTree,
        String dependencyTreeText,
        MavenDependencyAnalyzeResult dependencyAnalyzeResult
) {
}
