package com.jarscan.dto;

import java.util.List;

public record ScanComparisonResponse(
        StoredScanSummaryResponse baseline,
        StoredScanSummaryResponse target,
        ScanComparisonSummaryDiff summaryDiff,
        DependencyComparisonSection dependencyChanges,
        VulnerabilityComparisonSection vulnerabilityChanges,
        List<String> warnings,
        List<String> errors
) {
}
