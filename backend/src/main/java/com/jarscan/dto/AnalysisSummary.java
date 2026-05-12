package com.jarscan.dto;

public record AnalysisSummary(
        int totalArtifacts,
        int totalDependencies,
        int vulnerableDependencies,
        int totalVulnerabilities,
        int critical,
        int high,
        int medium,
        int low,
        int info,
        int unknown,
        Double highestCvss,
        String requiredJavaVersion
) {
}
