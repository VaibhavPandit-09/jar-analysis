package com.jarscan.dto;

import com.jarscan.model.SuppressionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateSuppressionRequest(
        @NotNull SuppressionType type,
        String groupId,
        String artifactId,
        String version,
        String cveId,
        @NotBlank String reason,
        Instant expiresAt,
        Boolean active
) {
}
