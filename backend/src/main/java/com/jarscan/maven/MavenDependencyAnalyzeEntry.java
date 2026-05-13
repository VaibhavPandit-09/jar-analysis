package com.jarscan.maven;

public record MavenDependencyAnalyzeEntry(
        String groupId,
        String artifactId,
        String type,
        String classifier,
        String version,
        String scope
) {
    public String dependencyKey() {
        return groupId + ":" + artifactId;
    }
}
