package com.jarscan.dto;

import com.jarscan.model.InputType;
import com.jarscan.model.JobStatus;
import com.jarscan.model.Severity;

import java.time.Instant;
import java.util.List;

public record StoredScanSummaryResponse(
        String scanId,
        String jobId,
        InputType inputType,
        String inputName,
        String inputHash,
        JobStatus status,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        int totalArtifacts,
        int totalDependencies,
        int totalVulnerabilities,
        int criticalCount,
        int highCount,
        int mediumCount,
        int lowCount,
        int infoCount,
        int unknownCount,
        Double highestCvss,
        String requiredJavaVersion,
        Severity highestSeverity,
        String createdAppVersion,
        String notes,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {
}
