package com.jarscan.dto;

import com.jarscan.model.InputType;
import com.jarscan.model.JobStatus;

import java.time.Instant;
import java.util.List;

public record AnalysisResult(
        String jobId,
        JobStatus status,
        InputType inputType,
        Instant startedAt,
        Instant completedAt,
        AnalysisSummary summary,
        List<ArtifactAnalysis> artifacts,
        String dependencyTreeText,
        List<String> warnings,
        List<String> errors
) {
}
