package com.jarscan.dto;

import com.jarscan.model.ModuleType;
import com.jarscan.model.Severity;

import java.util.List;
import java.util.Map;

public record ArtifactAnalysis(
        String id,
        String fileName,
        long sizeBytes,
        String sha256,
        int entryCount,
        boolean fatJar,
        String parentPath,
        int nestedDepth,
        MavenCoordinates coordinates,
        JavaVersionInfo javaVersion,
        ManifestInfo manifest,
        ModuleType moduleType,
        Severity highestSeverity,
        int vulnerabilityCount,
        List<DependencyInfo> dependencies,
        List<VulnerabilityFinding> vulnerabilities,
        List<ArtifactAnalysis> nestedArtifacts,
        Map<String, Object> rawMetadata,
        PackagingInspection packagingInspection
) {
}
