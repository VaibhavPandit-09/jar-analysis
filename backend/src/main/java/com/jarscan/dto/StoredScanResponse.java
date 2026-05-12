package com.jarscan.dto;

public record StoredScanResponse(
        StoredScanSummaryResponse summary,
        AnalysisResult result
) {
}
