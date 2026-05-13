package com.jarscan.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ConvergenceFinding(
        String groupId,
        String artifactId,
        List<String> versionsFound,
        Map<String, List<List<String>>> pathsByVersion,
        String selectedVersion,
        String recommendation,
        String snippet,
        boolean suppressed,
        String suppressionReason,
        Instant suppressionExpiresAt
) {
}
