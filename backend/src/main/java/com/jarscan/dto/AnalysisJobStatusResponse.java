package com.jarscan.dto;

import com.jarscan.model.InputType;
import com.jarscan.model.JobStatus;

import java.time.Instant;
import java.util.List;

public record AnalysisJobStatusResponse(
        String jobId,
        JobStatus status,
        InputType inputType,
        Instant startedAt,
        Instant completedAt,
        String message,
        List<String> warnings,
        List<String> errors
) {
}
