package com.jarscan.maven;

import java.util.List;

public record MavenDependencyAnalyzeResult(
        List<MavenDependencyAnalyzeEntry> usedDeclaredDependencies,
        List<MavenDependencyAnalyzeEntry> usedUndeclaredDependencies,
        List<MavenDependencyAnalyzeEntry> unusedDeclaredDependencies,
        String rawOutput
) {
}
