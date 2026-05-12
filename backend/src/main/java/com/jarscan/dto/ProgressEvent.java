package com.jarscan.dto;

import com.jarscan.model.ProgressEventType;
import com.jarscan.model.ProgressPhase;

import java.time.Instant;

public record ProgressEvent(
        String jobId,
        ProgressEventType type,
        ProgressPhase phase,
        String message,
        Integer percent,
        String currentItem,
        Integer completedItems,
        Integer totalItems,
        Instant timestamp
) {
}
