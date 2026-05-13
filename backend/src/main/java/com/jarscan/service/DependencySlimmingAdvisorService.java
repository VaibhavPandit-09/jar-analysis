package com.jarscan.service;

import com.jarscan.dto.AwsBundleAdvice;
import com.jarscan.dto.DependencyTree;
import com.jarscan.dto.DependencyTreeNode;
import com.jarscan.dto.DependencyUsageFinding;
import com.jarscan.dto.DuplicateClassFinding;
import com.jarscan.dto.SlimmingOpportunity;
import com.jarscan.dto.VersionConflictFinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DependencySlimmingAdvisorService {

    private static final List<String> AWS_SERVICE_MODULES = List.of("s3", "dynamodb", "sqs", "sns", "lambda", "kinesis", "cloudwatch", "ec2");

    public DependencySlimmingAdvisorResult analyze(
            List<DependencyUsageFinding> usageFindings,
            List<VersionConflictFinding> versionConflicts,
            List<DuplicateClassFinding> duplicateClassFindings,
            DependencyTree dependencyTree,
            Set<String> awsUsedServiceModules
    ) {
        Map<String, Integer> transitiveCounts = collectTransitiveCounts(dependencyTree);
        List<SlimmingOpportunity> opportunities = new ArrayList<>();

        for (DependencyUsageFinding finding : usageFindings) {
            if (!isUnusedLike(finding.status()) && !"USED_UNDECLARED".equals(finding.status())) {
                continue;
            }
            String dependency = dependencyName(finding.groupId(), finding.artifactId(), finding.version());
            String exclusionSnippet = buildExclusionSnippet(finding);
            String opportunityType = switch (finding.status()) {
                case "DECLARED_BUT_UNUSED", "APPARENTLY_UNUSED" -> "UNUSED_DIRECT_DEPENDENCY";
                case "PACKAGED_BUT_APPARENTLY_UNUSED" -> "UNUSED_PACKAGED_DEPENDENCY";
                case "USED_UNDECLARED" -> "USED_UNDECLARED_DEPENDENCY";
                default -> "DEPENDENCY_REVIEW";
            };
            List<String> evidence = new ArrayList<>(finding.evidence());
            Integer transitiveCount = transitiveCounts.get(gaKey(finding.groupId(), finding.artifactId()));
            if (transitiveCount != null && transitiveCount > 0) {
                evidence.add("This dependency currently introduces " + transitiveCount + " transitive child dependencies in the parsed tree.");
            }
            if (finding.sizeBytes() != null && finding.sizeBytes() > 5_000_000) {
                evidence.add("This artifact is larger than 5 MB, so removing it could noticeably shrink the packaged dependency set.");
            }
            if (finding.vulnerabilitiesContributed() != null && finding.vulnerabilitiesContributed() > 0) {
                evidence.add("This dependency contributes " + finding.vulnerabilitiesContributed() + " known vulnerability finding(s).");
            }

            opportunities.add(new SlimmingOpportunity(
                    titleForFinding(finding),
                    dependency,
                    opportunityType,
                    finding.sizeBytes(),
                    transitiveCount,
                    finding.vulnerabilitiesContributed(),
                    finding.status(),
                    finding.confidence(),
                    List.copyOf(evidence),
                    null,
                    "USED_UNDECLARED".equals(finding.status()) ? dependencySnippet(finding.groupId(), finding.artifactId(), finding.version()) : null,
                    exclusionSnippet,
                    mergeWarnings(finding.warnings(), exclusionSnippet != null ? "Review and test before applying exclusions." : null)
            ));
        }

        for (VersionConflictFinding conflict : versionConflicts) {
            opportunities.add(new SlimmingOpportunity(
                    "Reduce version sprawl for " + dependencyName(conflict.groupId(), conflict.artifactId(), conflict.resolvedVersion()),
                    dependencyName(conflict.groupId(), conflict.artifactId(), conflict.resolvedVersion()),
                    "VERSION_CONFLICT_REDUCTION",
                    null,
                    transitiveCounts.get(gaKey(conflict.groupId(), conflict.artifactId())),
                    null,
                    "UNKNOWN",
                    conflict.riskLevel(),
                    List.of(
                            "Multiple versions were requested for this dependency.",
                            "Aligning versions can reduce duplicate classes, convergence churn, and bundle size over time."
                    ),
                    null,
                    conflict.dependencyManagementSnippet(),
                    null,
                    List.of(DependencyUsageAnalysisService.RUNTIME_WARNING)
            ));
        }

        if (!duplicateClassFindings.isEmpty()) {
            opportunities.add(new SlimmingOpportunity(
                    "Review duplicate or overlapping libraries",
                    duplicateClassFindings.getFirst().symbol(),
                    "DUPLICATE_LIBRARY_REDUCTION",
                    null,
                    null,
                    null,
                    "UNKNOWN",
                    "MEDIUM",
                    List.of("Duplicate classes or split packages were detected in the packaged dependency set."),
                    null,
                    null,
                    null,
                    List.of(DependencyUsageAnalysisService.RUNTIME_WARNING, "Review and test before removing duplicate providers.")
            ));
        }

        AwsBundleAdvice awsBundleAdvice = buildAwsAdvice(usageFindings, awsUsedServiceModules);
        if (awsBundleAdvice != null) {
            opportunities.add(new SlimmingOpportunity(
                    "Replace broad AWS bundle with narrower service modules",
                    awsBundleAdvice.detectedArtifact(),
                    "AWS_BUNDLE_REPLACEMENT",
                    null,
                    null,
                    null,
                    "POSSIBLY_RUNTIME_USED",
                    awsBundleAdvice.confidence(),
                    List.of(
                            "A broad AWS SDK bundle was detected.",
                            "Only a subset of AWS service client packages were seen in application evidence."
                    ),
                    awsBundleAdvice.suggestedReplacement(),
                    awsBundleAdvice.mavenSnippet(),
                    null,
                    awsBundleAdvice.warnings()
            ));
        }

        opportunities.sort(Comparator
                .comparing((SlimmingOpportunity opportunity) -> slimmingRank(opportunity.opportunityType()))
                .thenComparing(SlimmingOpportunity::title));
        return new DependencySlimmingAdvisorResult(List.copyOf(opportunities), awsBundleAdvice);
    }

    private AwsBundleAdvice buildAwsAdvice(List<DependencyUsageFinding> usageFindings, Set<String> awsUsedServiceModules) {
        DependencyUsageFinding bundleFinding = usageFindings.stream()
                .filter(finding -> isAwsBundle(finding.groupId(), finding.artifactId()))
                .findFirst()
                .orElse(null);
        if (bundleFinding == null) {
            return null;
        }

        List<String> usedModules = awsUsedServiceModules.stream().sorted().toList();
        if (usedModules.isEmpty()) {
            return new AwsBundleAdvice(
                    dependencyName(bundleFinding.groupId(), bundleFinding.artifactId(), bundleFinding.version()),
                    List.of(),
                    AWS_SERVICE_MODULES,
                    null,
                    null,
                    List.of(
                            DependencyUsageAnalysisService.RUNTIME_WARNING,
                            "No clear AWS service client references were found, so bundle replacement guidance stays low confidence."
                    ),
                    "LOW"
            );
        }

        List<String> unusedModules = AWS_SERVICE_MODULES.stream().filter(module -> !awsUsedServiceModules.contains(module)).toList();
        String primaryModule = usedModules.getFirst();
        String replacement = bundleFinding.groupId() != null && bundleFinding.groupId().equals("com.amazonaws")
                ? "com.amazonaws:aws-java-sdk-" + primaryModule
                : "software.amazon.awssdk:" + primaryModule;
        String versionExpression = bundleFinding.groupId() != null && bundleFinding.groupId().equals("com.amazonaws")
                ? "${aws.sdk.version}"
                : "${aws.sdk.version}";
        return new AwsBundleAdvice(
                dependencyName(bundleFinding.groupId(), bundleFinding.artifactId(), bundleFinding.version()),
                usedModules,
                unusedModules,
                replacement,
                dependencySnippet(replacement.split(":")[0], replacement.split(":")[1], versionExpression),
                List.of(
                        DependencyUsageAnalysisService.RUNTIME_WARNING,
                        "Review runtime/configuration usage and test before removing the bundle."
                ),
                usedModules.size() == 1 ? "HIGH" : "MEDIUM"
        );
    }

    private boolean isAwsBundle(String groupId, String artifactId) {
        if (groupId == null || artifactId == null) {
            return false;
        }
        return (groupId.equals("software.amazon.awssdk") && artifactId.equals("bundle"))
                || artifactId.equals("aws-java-sdk-bundle")
                || (groupId.equals("com.amazonaws") && artifactId.equals("aws-java-sdk"));
    }

    private boolean isUnusedLike(String status) {
        return "DECLARED_BUT_UNUSED".equals(status)
                || "APPARENTLY_UNUSED".equals(status)
                || "PACKAGED_BUT_APPARENTLY_UNUSED".equals(status);
    }

    private String dependencyName(String groupId, String artifactId, String version) {
        return (groupId == null ? "unknown" : groupId)
                + ":"
                + (artifactId == null ? "unknown" : artifactId)
                + (version == null ? "" : ":" + version);
    }

    private String titleForFinding(DependencyUsageFinding finding) {
        return switch (finding.status()) {
            case "DECLARED_BUT_UNUSED" -> "Review declared but unused dependency " + dependencyName(finding.groupId(), finding.artifactId(), finding.version());
            case "APPARENTLY_UNUSED" -> "Review apparently unused dependency " + dependencyName(finding.groupId(), finding.artifactId(), finding.version());
            case "PACKAGED_BUT_APPARENTLY_UNUSED" -> "Review packaged but apparently unused dependency " + dependencyName(finding.groupId(), finding.artifactId(), finding.version());
            case "USED_UNDECLARED" -> "Declare used undeclared dependency " + dependencyName(finding.groupId(), finding.artifactId(), finding.version());
            default -> "Review dependency " + dependencyName(finding.groupId(), finding.artifactId(), finding.version());
        };
    }

    private String dependencySnippet(String groupId, String artifactId, String version) {
        if (groupId == null || artifactId == null || version == null) {
            return null;
        }
        return """
                <dependency>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </dependency>
                """.formatted(groupId, artifactId, version);
    }

    private String buildExclusionSnippet(DependencyUsageFinding finding) {
        if (finding.paths() == null || finding.paths().isEmpty()) {
            return null;
        }
        List<String> path = finding.paths().getFirst();
        if (path.size() < 3) {
            return null;
        }
        Coordinate parent = parseCoordinate(path.get(1));
        if (parent == null || finding.groupId() == null || finding.artifactId() == null) {
            return null;
        }
        String version = parent.version() == null ? "..." : parent.version();
        return """
                <dependency>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                  <exclusions>
                    <exclusion>
                      <groupId>%s</groupId>
                      <artifactId>%s</artifactId>
                    </exclusion>
                  </exclusions>
                </dependency>
                """.formatted(parent.groupId(), parent.artifactId(), version, finding.groupId(), finding.artifactId());
    }

    private Coordinate parseCoordinate(String value) {
        String[] parts = value.split(":");
        if (parts.length < 2) {
            return null;
        }
        return new Coordinate(parts[0], parts[1], parts.length >= 3 ? parts[2] : null);
    }

    private Map<String, Integer> collectTransitiveCounts(DependencyTree dependencyTree) {
        if (dependencyTree == null || dependencyTree.roots() == null) {
            return Map.of();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DependencyTreeNode root : dependencyTree.roots()) {
            collectTransitiveCounts(root, counts);
        }
        return counts;
    }

    private void collectTransitiveCounts(DependencyTreeNode node, Map<String, Integer> counts) {
        if (node.depth() > 0 && node.groupId() != null && node.artifactId() != null) {
            counts.put(gaKey(node.groupId(), node.artifactId()), Math.max(counts.getOrDefault(gaKey(node.groupId(), node.artifactId()), 0), countDescendants(node)));
        }
        node.children().forEach(child -> collectTransitiveCounts(child, counts));
    }

    private int countDescendants(DependencyTreeNode node) {
        int count = node.children().size();
        for (DependencyTreeNode child : node.children()) {
            count += countDescendants(child);
        }
        return count;
    }

    private List<String> mergeWarnings(List<String> warnings, String additionalWarning) {
        Set<String> merged = new LinkedHashSet<>(warnings == null ? List.of() : warnings);
        if (additionalWarning != null) {
            merged.add(additionalWarning);
        }
        return List.copyOf(merged);
    }

    private int slimmingRank(String opportunityType) {
        return switch (opportunityType == null ? "" : opportunityType.toUpperCase(Locale.ROOT)) {
            case "AWS_BUNDLE_REPLACEMENT" -> 0;
            case "UNUSED_DIRECT_DEPENDENCY", "UNUSED_PACKAGED_DEPENDENCY" -> 1;
            case "VERSION_CONFLICT_REDUCTION" -> 2;
            case "DUPLICATE_LIBRARY_REDUCTION" -> 3;
            default -> 4;
        };
    }

    private String gaKey(String groupId, String artifactId) {
        return groupId + ":" + artifactId;
    }

    private record Coordinate(String groupId, String artifactId, String version) {
    }

    public record DependencySlimmingAdvisorResult(
            List<SlimmingOpportunity> opportunities,
            AwsBundleAdvice awsBundleAdvice
    ) {
    }
}
