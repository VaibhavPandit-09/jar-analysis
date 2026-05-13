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
        String requiredJavaVersion,
        int versionConflictCount,
        int convergenceIssueCount,
        int duplicateClassCount,
        int licenseWarningCount,
        int permissiveLicenseCount,
        int weakCopyleftLicenseCount,
        int strongCopyleftLicenseCount,
        int unknownLicenseCount,
        int multipleLicenseCount,
        int apparentlyUnusedDependencyCount,
        int possiblyRuntimeUsedDependencyCount,
        int slimmingOpportunityCount,
        long estimatedRemovableSizeBytes,
        int awsBundleWarningCount,
        int policyWarningCount,
        int policyFailureCount,
        String overallPolicyStatus
) {
}
