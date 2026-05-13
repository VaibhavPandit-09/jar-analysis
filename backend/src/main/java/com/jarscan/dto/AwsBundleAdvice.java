package com.jarscan.dto;

import java.util.List;

public record AwsBundleAdvice(
        String detectedArtifact,
        List<String> usedServiceModules,
        List<String> apparentlyUnusedServiceModules,
        String suggestedReplacement,
        String mavenSnippet,
        List<String> warnings,
        String confidence
) {
}
