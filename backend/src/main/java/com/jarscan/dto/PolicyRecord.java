package com.jarscan.dto;

import com.jarscan.model.PolicySeverity;

import java.time.Instant;
import java.util.Map;

public record PolicyRecord(
        String id,
        String name,
        String description,
        String ruleType,
        PolicySeverity severity,
        boolean enabled,
        Map<String, Object> config,
        Instant createdAt,
        Instant updatedAt
) {
}
