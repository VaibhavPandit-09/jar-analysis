package com.jarscan.dto;

import java.time.Instant;
import java.util.List;

public record LicenseFinding(
        String groupId,
        String artifactId,
        String version,
        String licenseName,
        String licenseUrl,
        String source,
        String confidence,
        String category,
        List<String> warnings,
        boolean suppressed,
        String suppressionReason,
        Instant suppressionExpiresAt
) {
}
