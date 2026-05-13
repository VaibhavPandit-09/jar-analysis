package com.jarscan.dto;

import java.time.Instant;

public record UpdateSuppressionRequest(
        String groupId,
        String artifactId,
        String version,
        String cveId,
        String reason,
        Instant expiresAt,
        Boolean active
) {
}
