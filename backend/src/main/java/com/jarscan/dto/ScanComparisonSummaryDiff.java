package com.jarscan.dto;

public record ScanComparisonSummaryDiff(
        CountDiff totalArtifacts,
        CountDiff totalDependencies,
        CountDiff totalVulnerabilities,
        CountDiff critical,
        CountDiff high,
        CountDiff medium,
        CountDiff low,
        DoubleDiff highestCvss,
        String beforePolicyStatus,
        String afterPolicyStatus,
        CountDiff licenseCount
) {
}
