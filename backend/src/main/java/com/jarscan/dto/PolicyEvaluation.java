package com.jarscan.dto;

import java.util.List;

public record PolicyEvaluation(
        String overallStatus,
        int passedCount,
        int warningCount,
        int failedCount,
        List<PolicyFinding> findings
) {
}
