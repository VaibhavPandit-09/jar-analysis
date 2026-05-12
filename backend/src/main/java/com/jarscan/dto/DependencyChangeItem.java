package com.jarscan.dto;

public record DependencyChangeItem(
        DependencyChangeType changeType,
        String artifactKey,
        String oldGroupId,
        String oldArtifactId,
        String newGroupId,
        String newArtifactId,
        String oldVersion,
        String newVersion,
        String oldJavaVersion,
        String newJavaVersion,
        Integer oldVulnerabilityCount,
        Integer newVulnerabilityCount,
        String scope,
        boolean coordinatesChanged,
        boolean versionChanged,
        boolean javaVersionChanged,
        boolean vulnerabilityCountChanged
) {
}
