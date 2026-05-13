package com.jarscan.dto;

import java.util.List;

public record SlimmingOpportunity(
        String title,
        String dependency,
        String opportunityType,
        Long sizeImpactBytes,
        Integer transitiveDependencyCount,
        Integer vulnerabilitiesContributed,
        String usageStatus,
        String confidence,
        List<String> evidence,
        String suggestedReplacement,
        String mavenSnippet,
        String exclusionSnippet,
        List<String> warnings
) {
}
