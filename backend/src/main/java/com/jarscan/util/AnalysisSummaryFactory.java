package com.jarscan.util;

import com.jarscan.dto.AnalysisSummary;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.VulnerabilityFinding;
import com.jarscan.model.InputType;
import com.jarscan.model.Severity;

import java.util.ArrayList;
import java.util.List;

public final class AnalysisSummaryFactory {

    private AnalysisSummaryFactory() {
    }

    public static AnalysisSummary create(InputType inputType, List<ArtifactAnalysis> artifacts) {
        List<ArtifactAnalysis> flattenedArtifacts = flattenArtifacts(artifacts);
        List<VulnerabilityFinding> findings = flattenedArtifacts.stream()
                .flatMap(artifact -> artifact.vulnerabilities().stream())
                .toList();

        int critical = countSeverity(findings, Severity.CRITICAL);
        int high = countSeverity(findings, Severity.HIGH);
        int medium = countSeverity(findings, Severity.MEDIUM);
        int low = countSeverity(findings, Severity.LOW);
        int info = countSeverity(findings, Severity.INFO);
        int unknown = countSeverity(findings, Severity.UNKNOWN);
        Double highestCvss = findings.stream()
                .map(VulnerabilityFinding::cvssScore)
                .filter(score -> score != null)
                .max(Double::compareTo)
                .orElse(null);

        Integer maxMajor = flattenedArtifacts.stream()
                .map(artifact -> artifact.javaVersion().maxMajor())
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(null);

        long vulnerableDependencies = flattenedArtifacts.stream()
                .filter(artifact -> artifact.vulnerabilityCount() > 0 || artifact.highestSeverity() != Severity.UNKNOWN)
                .count();

        int totalDependencies = inputType == InputType.POM
                ? flattenedArtifacts.size()
                : Math.max(0, flattenedArtifacts.size() - artifacts.size());

        return new AnalysisSummary(
                flattenedArtifacts.size(),
                totalDependencies,
                (int) vulnerableDependencies,
                findings.size(),
                critical,
                high,
                medium,
                low,
                info,
                unknown,
                highestCvss,
                JavaVersionMapper.describe(maxMajor)
        );
    }

    public static List<ArtifactAnalysis> flattenArtifacts(List<ArtifactAnalysis> artifacts) {
        List<ArtifactAnalysis> flattened = new ArrayList<>();
        for (ArtifactAnalysis artifact : artifacts) {
            flattened.add(artifact);
            flattened.addAll(flattenArtifacts(artifact.nestedArtifacts()));
        }
        return flattened;
    }

    private static int countSeverity(List<VulnerabilityFinding> findings, Severity severity) {
        return (int) findings.stream().filter(finding -> finding.severity() == severity).count();
    }
}
