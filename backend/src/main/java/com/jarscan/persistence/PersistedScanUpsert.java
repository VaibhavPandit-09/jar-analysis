package com.jarscan.persistence;

import com.jarscan.dto.AnalysisResult;
import com.jarscan.model.InputType;
import com.jarscan.model.JobStatus;

import java.time.Instant;
import java.util.List;

public record PersistedScanUpsert(
        String id,
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
        String createdAppVersion,
        String notes,
        List<String> tags,
        String resultJson,
        AnalysisResult result,
        Instant createdAt,
        Instant updatedAt
) {
}
