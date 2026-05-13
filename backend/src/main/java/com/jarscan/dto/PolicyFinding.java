package com.jarscan.dto;

import java.time.Instant;
import java.util.List;

public record PolicyFinding(
        String policyId,
        String policyName,
        String ruleType,
        String status,
        String severity,
        String message,
        List<String> affectedDependencies,
        String recommendation,
        boolean suppressed,
        String suppressionReason,
        Instant suppressionExpiresAt
) {
}
