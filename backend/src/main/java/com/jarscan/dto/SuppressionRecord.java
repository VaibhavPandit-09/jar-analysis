package com.jarscan.dto;

import com.jarscan.model.SuppressionType;

import java.time.Instant;

public record SuppressionRecord(
        String id,
        SuppressionType type,
        String groupId,
        String artifactId,
        String version,
        String cveId,
        String reason,
        Instant expiresAt,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
