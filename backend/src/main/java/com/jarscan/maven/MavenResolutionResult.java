package com.jarscan.maven;

import java.nio.file.Path;
import java.util.List;

public record MavenResolutionResult(
        List<Path> resolvedArtifacts,
        String dependencyTreeText
) {
}
