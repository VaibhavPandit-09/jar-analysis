package com.jarscan.dto;

import java.util.List;
import java.util.Map;

public record VersionConflictFinding(
        String groupId,
        String artifactId,
        String resolvedVersion,
        List<String> requestedVersions,
        Map<String, List<List<String>>> pathsByVersion,
        String conflictType,
        String riskLevel,
        String recommendation,
        String dependencyManagementSnippet
) {
}
