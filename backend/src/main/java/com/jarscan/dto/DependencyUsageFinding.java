package com.jarscan.dto;

import java.util.List;

public record DependencyUsageFinding(
        String groupId,
        String artifactId,
        String version,
        String status,
        String confidence,
        List<String> evidence,
        List<String> warnings,
        String suggestedAction,
        List<List<String>> paths,
        Long sizeBytes,
        Integer vulnerabilitiesContributed
) {
}
