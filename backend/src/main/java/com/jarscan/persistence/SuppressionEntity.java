package com.jarscan.persistence;

import com.jarscan.model.SuppressionType;

import java.time.Instant;

public record SuppressionEntity(
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
