package com.jarscan.service;

import com.jarscan.dto.AnalysisResult;
import com.jarscan.dto.AnalysisSummary;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.CreatePolicyRequest;
import com.jarscan.dto.DependencyUsageFinding;
import com.jarscan.dto.PolicyEvaluation;
import com.jarscan.dto.PolicyFinding;
import com.jarscan.dto.PolicyRecord;
import com.jarscan.dto.UpdatePolicyRequest;
import com.jarscan.model.PolicySeverity;
import com.jarscan.model.PolicyStatus;
import com.jarscan.persistence.PolicyEntity;
import com.jarscan.persistence.PolicyRepository;
import com.jarscan.util.AnalysisSummaryFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PolicyService {

    private static final String RULE_CRITICAL_VULNERABILITIES = "CRITICAL_VULNERABILITIES";
    private static final String RULE_HIGH_VULNERABILITIES = "HIGH_VULNERABILITIES";
    private static final String RULE_UNKNOWN_LICENSES = "UNKNOWN_LICENSES";
    private static final String RULE_STRONG_COPYLEFT = "STRONG_COPYLEFT";
    private static final String RULE_APPARENTLY_UNUSED = "APPARENTLY_UNUSED_DEPENDENCIES";
    private static final String RULE_DUPLICATE_CLASSES = "DUPLICATE_CLASSES";
    private static final String RULE_MULTIPLE_VERSIONS = "MULTIPLE_VERSIONS";
    private static final String RULE_JAVA_VERSION = "JAVA_VERSION_LIMIT";
    private static final String RULE_SNAPSHOT = "SNAPSHOT_DEPENDENCIES";
    private static final String RULE_BROAD_BUNDLE = "BROAD_BUNDLE_DEPENDENCY";

    private final PolicyRepository repository;

    public PolicyService(PolicyRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void ensureBuiltIns() {
        Instant now = Instant.now();
        for (PolicyEntity policy : builtIns(now)) {
            repository.upsert(policy);
        }
    }

    public List<PolicyRecord> listPolicies() {
        ensureBuiltIns();
        return repository.findAll().stream().map(this::toRecord).toList();
    }

    public PolicyRecord createPolicy(CreatePolicyRequest request) {
        ensureBuiltIns();
        Instant now = Instant.now();
        PolicyEntity entity = new PolicyEntity(
                request.id().isBlank() ? UUID.randomUUID().toString() : request.id(),
                request.name(),
                request.description(),
                request.ruleType(),
                request.severity(),
                request.enabled() == null || request.enabled(),
                request.config() == null ? Map.of() : request.config(),
                now,
                now
        );
        repository.upsert(entity);
        return toRecord(entity);
    }

    public PolicyRecord updatePolicy(String id, UpdatePolicyRequest request) {
        PolicyEntity existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown policy: " + id));
        PolicyEntity updated = new PolicyEntity(
                existing.id(),
                request.name() == null ? existing.name() : request.name(),
                request.description() == null ? existing.description() : request.description(),
                existing.ruleType(),
                request.severity() == null ? existing.severity() : request.severity(),
                request.enabled() == null ? existing.enabled() : request.enabled(),
                request.config() == null ? existing.config() : request.config(),
                existing.createdAt(),
                Instant.now()
        );
        return toRecord(repository.update(updated).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown policy: " + id)));
    }

    public void deletePolicy(String id) {
        if (!repository.deleteById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown policy: " + id);
        }
    }

    public PolicyEvaluation evaluate(AnalysisResult result) {
        ensureBuiltIns();
        return evaluate(result, repository.findEnabled());
    }

    public AnalysisResult applyPolicies(AnalysisResult result) {
        PolicyEvaluation evaluation = evaluate(result);
        return withPolicyEvaluation(result, evaluation);
    }

    public AnalysisResult withPolicyEvaluation(AnalysisResult result, PolicyEvaluation evaluation) {
        AnalysisSummary summary = result.summary() == null ? null : new AnalysisSummary(
                result.summary().totalArtifacts(),
                result.summary().totalDependencies(),
                result.summary().vulnerableDependencies(),
                result.summary().totalVulnerabilities(),
                result.summary().critical(),
                result.summary().high(),
                result.summary().medium(),
                result.summary().low(),
                result.summary().info(),
                result.summary().unknown(),
                result.summary().highestCvss(),
                result.summary().requiredJavaVersion(),
                result.summary().versionConflictCount(),
                result.summary().convergenceIssueCount(),
                result.summary().duplicateClassCount(),
                result.summary().licenseWarningCount(),
                result.summary().permissiveLicenseCount(),
                result.summary().weakCopyleftLicenseCount(),
                result.summary().strongCopyleftLicenseCount(),
                result.summary().unknownLicenseCount(),
                result.summary().multipleLicenseCount(),
                result.summary().apparentlyUnusedDependencyCount(),
                result.summary().possiblyRuntimeUsedDependencyCount(),
                result.summary().slimmingOpportunityCount(),
                result.summary().estimatedRemovableSizeBytes(),
                result.summary().awsBundleWarningCount(),
                evaluation.warningCount(),
                evaluation.failedCount(),
                evaluation.overallStatus()
        );

        return new AnalysisResult(
                result.jobId(),
                result.status(),
                result.inputType(),
                result.startedAt(),
                result.completedAt(),
                summary,
                result.artifacts(),
                result.dependencyTree(),
                result.versionConflicts(),
                result.convergenceFindings(),
                result.duplicateClasses(),
                result.licenses(),
                result.dependencyUsage(),
                result.slimmingOpportunities(),
                result.awsBundleAdvice(),
                evaluation,
                result.dependencyTreeText(),
                result.warnings(),
                result.errors(),
                result.projectStructure()
        );
    }

    public AnalysisResult refreshPolicySummary(AnalysisResult result) {
        PolicyEvaluation evaluation = result.policyEvaluation();
        if (evaluation == null) {
            return result;
        }
        int warnings = (int) evaluation.findings().stream()
                .filter(finding -> !finding.suppressed() && PolicyStatus.WARNING.name().equals(finding.status()))
                .count();
        int failures = (int) evaluation.findings().stream()
                .filter(finding -> !finding.suppressed() && PolicyStatus.FAILED.name().equals(finding.status()))
                .count();
        int passed = (int) evaluation.findings().stream()
                .filter(finding -> PolicyStatus.PASSED.name().equals(finding.status()) || finding.suppressed())
                .count();
        String overall = failures > 0 ? PolicyStatus.FAILED.name() : warnings > 0 ? PolicyStatus.WARNING.name() : PolicyStatus.PASSED.name();
        return withPolicyEvaluation(result, new PolicyEvaluation(overall, passed, warnings, failures, evaluation.findings()));
    }

    private PolicyEvaluation evaluate(AnalysisResult result, List<PolicyEntity> policies) {
        List<PolicyFinding> findings = new ArrayList<>();
        for (PolicyEntity policy : policies) {
            findings.add(evaluatePolicy(policy, result));
        }
        findings.sort(Comparator.comparing(PolicyFinding::policyName));
        int warnings = (int) findings.stream().filter(finding -> PolicyStatus.WARNING.name().equals(finding.status())).count();
        int failures = (int) findings.stream().filter(finding -> PolicyStatus.FAILED.name().equals(finding.status())).count();
        int passed = (int) findings.stream().filter(finding -> PolicyStatus.PASSED.name().equals(finding.status())).count();
        String overall = failures > 0 ? PolicyStatus.FAILED.name() : warnings > 0 ? PolicyStatus.WARNING.name() : PolicyStatus.PASSED.name();
        return new PolicyEvaluation(overall, passed, warnings, failures, findings);
    }

    private PolicyFinding evaluatePolicy(PolicyEntity policy, AnalysisResult result) {
        Map<String, Object> config = policy.config() == null ? Map.of() : policy.config();
        List<ArtifactAnalysis> flattenedArtifacts = AnalysisSummaryFactory.flattenArtifacts(result.artifacts());
        return switch (policy.ruleType()) {
            case RULE_CRITICAL_VULNERABILITIES -> thresholdFinding(policy, visibleSeverityCount(result, com.jarscan.model.Severity.CRITICAL), 1, "critical vulnerabilities", "Review and remediate or suppress accepted risk with a reason.", config, result);
            case RULE_HIGH_VULNERABILITIES -> thresholdFinding(policy, visibleSeverityCount(result, com.jarscan.model.Severity.HIGH), 1, "high vulnerabilities", "Prioritize high-severity dependency upgrades or document the accepted risk.", config, result);
            case RULE_UNKNOWN_LICENSES -> thresholdFinding(policy, visibleCount(result.licenses().stream().filter(license -> !license.suppressed() && "unknown".equals(license.category())).toList()), 1, "unknown licenses", "Review the artifact metadata and confirm the correct license before approving distribution.", config, result);
            case RULE_STRONG_COPYLEFT -> thresholdFinding(policy, visibleCount(result.licenses().stream().filter(license -> !license.suppressed() && "strong copyleft".equals(license.category())).toList()), 1, "strong copyleft licenses", "Review distribution obligations and replace the dependency if the license is incompatible.", config, result);
            case RULE_APPARENTLY_UNUSED -> thresholdFinding(policy,
                    visibleCount(result.dependencyUsage().stream().filter(finding -> !finding.suppressed() && Set.of("APPARENTLY_UNUSED", "DECLARED_BUT_UNUSED", "PACKAGED_BUT_APPARENTLY_UNUSED").contains(finding.status())).toList()),
                    1,
                    "apparently unused dependencies",
                    "Review the evidence carefully and test before removing or excluding dependencies.",
                    config,
                    result);
            case RULE_DUPLICATE_CLASSES -> thresholdFinding(policy, visibleCount(result.duplicateClasses().stream().filter(finding -> !finding.suppressed()).toList()), 1, "duplicate class findings", "Reduce classpath collisions before packaging or deployment.", config, result);
            case RULE_MULTIPLE_VERSIONS -> thresholdFinding(policy, visibleCount(result.versionConflicts().stream().filter(finding -> !finding.suppressed()).toList()), 1, "multiple-version dependency conflicts", "Pin the selected version or narrow the dependency graph to reduce drift.", config, result);
            case RULE_JAVA_VERSION -> javaVersionFinding(policy, result, config);
            case RULE_SNAPSHOT -> snapshotFinding(policy, flattenedArtifacts, config);
            case RULE_BROAD_BUNDLE -> broadBundleFinding(policy, result, config);
            default -> new PolicyFinding(
                    policy.id(),
                    policy.name(),
                    policy.ruleType(),
                    PolicyStatus.PASSED.name(),
                    policy.severity().name(),
                    "No evaluator is registered for this policy rule type yet.",
                    List.of(),
                    "Review the policy configuration or extend the evaluator in a future session.",
                    false,
                    null,
                    null
            );
        };
    }

    private PolicyFinding thresholdFinding(
            PolicyEntity policy,
            int actualCount,
            int defaultThreshold,
            String subject,
            String recommendation,
            Map<String, Object> config,
            AnalysisResult result
    ) {
        int threshold = integerConfig(config, "threshold", defaultThreshold);
        boolean triggered = actualCount >= threshold;
        String status = !triggered ? PolicyStatus.PASSED.name() : policy.severity() == PolicySeverity.FAIL ? PolicyStatus.FAILED.name() : PolicyStatus.WARNING.name();
        String message = triggered
                ? "Detected " + actualCount + " " + subject + " (threshold " + threshold + ")."
                : "Detected " + actualCount + " " + subject + "; threshold is " + threshold + ".";
        return new PolicyFinding(
                policy.id(),
                policy.name(),
                policy.ruleType(),
                status,
                policy.severity().name(),
                message,
                summarizeAffectedDependencies(result, policy.ruleType()),
                recommendation,
                false,
                null,
                null
        );
    }

    private PolicyFinding javaVersionFinding(PolicyEntity policy, AnalysisResult result, Map<String, Object> config) {
        int maxJava = integerConfig(config, "maxJavaFeature", 17);
        int required = parseJavaFeature(result.summary().requiredJavaVersion());
        boolean triggered = required > maxJava;
        String status = !triggered ? PolicyStatus.PASSED.name() : policy.severity() == PolicySeverity.FAIL ? PolicyStatus.FAILED.name() : PolicyStatus.WARNING.name();
        return new PolicyFinding(
                policy.id(),
                policy.name(),
                policy.ruleType(),
                status,
                policy.severity().name(),
                triggered
                        ? "The scan requires Java " + required + ", which exceeds the configured limit of Java " + maxJava + "."
                        : "The scan requires Java " + required + ", which is within the configured limit of Java " + maxJava + ".",
                result.artifacts().stream().map(artifact -> artifact.fileName()).limit(6).toList(),
                "Align the runtime baseline or replace dependencies that force a newer Java level.",
                false,
                null,
                null
        );
    }

    private PolicyFinding snapshotFinding(PolicyEntity policy, List<ArtifactAnalysis> artifacts, Map<String, Object> config) {
        List<String> matches = artifacts.stream()
                .filter(artifact -> artifact.coordinates().version() != null && artifact.coordinates().version().endsWith("-SNAPSHOT"))
                .map(artifact -> coordinate(artifact.coordinates().groupId(), artifact.coordinates().artifactId(), artifact.coordinates().version()))
                .distinct()
                .toList();
        int threshold = integerConfig(config, "threshold", 1);
        boolean triggered = matches.size() >= threshold;
        String status = !triggered ? PolicyStatus.PASSED.name() : policy.severity() == PolicySeverity.FAIL ? PolicyStatus.FAILED.name() : PolicyStatus.WARNING.name();
        return new PolicyFinding(
                policy.id(),
                policy.name(),
                policy.ruleType(),
                status,
                policy.severity().name(),
                triggered ? "Detected " + matches.size() + " SNAPSHOT dependencies." : "No SNAPSHOT dependencies were detected.",
                matches,
                "Replace SNAPSHOT dependencies with reproducible releases before long-lived deployment.",
                false,
                null,
                null
        );
    }

    private PolicyFinding broadBundleFinding(PolicyEntity policy, AnalysisResult result, Map<String, Object> config) {
        List<String> matches = new ArrayList<>();
        if (result.awsBundleAdvice() != null && result.awsBundleAdvice().detectedArtifact() != null) {
            matches.add(result.awsBundleAdvice().detectedArtifact());
        }
        result.dependencyUsage().stream()
                .filter(finding -> coordinate(finding.groupId(), finding.artifactId(), finding.version()).contains("aws-java-sdk-bundle")
                        || coordinate(finding.groupId(), finding.artifactId(), finding.version()).contains("software.amazon.awssdk:bundle"))
                .map(finding -> coordinate(finding.groupId(), finding.artifactId(), finding.version()))
                .filter(item -> !matches.contains(item))
                .forEach(matches::add);
        boolean triggered = matches.size() >= integerConfig(config, "threshold", 1);
        String status = !triggered ? PolicyStatus.PASSED.name() : policy.severity() == PolicySeverity.FAIL ? PolicyStatus.FAILED.name() : PolicyStatus.WARNING.name();
        return new PolicyFinding(
                policy.id(),
                policy.name(),
                policy.ruleType(),
                status,
                policy.severity().name(),
                triggered ? "Detected broad bundle dependencies that may hide unnecessary transitive modules." : "No broad bundle dependencies were detected.",
                matches,
                "Prefer narrower modules where evidence shows only a subset of services is used.",
                false,
                null,
                null
        );
    }

    private List<String> summarizeAffectedDependencies(AnalysisResult result, String ruleType) {
        return switch (ruleType) {
            case RULE_CRITICAL_VULNERABILITIES, RULE_HIGH_VULNERABILITIES -> result.artifacts().stream()
                    .filter(artifact -> artifact.vulnerabilityCount() > 0)
                    .map(artifact -> coordinate(artifact.coordinates().groupId(), artifact.coordinates().artifactId(), artifact.coordinates().version()))
                    .filter(value -> !"unknown:unknown:unknown".equals(value))
                    .distinct()
                    .limit(8)
                    .toList();
            case RULE_UNKNOWN_LICENSES, RULE_STRONG_COPYLEFT -> result.licenses().stream()
                    .map(license -> coordinate(license.groupId(), license.artifactId(), license.version()))
                    .distinct()
                    .limit(8)
                    .toList();
            case RULE_APPARENTLY_UNUSED -> result.dependencyUsage().stream()
                    .filter(finding -> Set.of("APPARENTLY_UNUSED", "DECLARED_BUT_UNUSED", "PACKAGED_BUT_APPARENTLY_UNUSED").contains(finding.status()))
                    .map(finding -> coordinate(finding.groupId(), finding.artifactId(), finding.version()))
                    .distinct()
                    .limit(8)
                    .toList();
            case RULE_DUPLICATE_CLASSES -> result.duplicateClasses().stream().map(finding -> finding.symbol()).distinct().limit(8).toList();
            case RULE_MULTIPLE_VERSIONS -> result.versionConflicts().stream().map(finding -> coordinate(finding.groupId(), finding.artifactId(), finding.resolvedVersion())).distinct().limit(8).toList();
            default -> List.of();
        };
    }

    private int visibleCount(List<?> findings) {
        return findings.size();
    }

    private int visibleSeverityCount(AnalysisResult result, com.jarscan.model.Severity severity) {
        return AnalysisSummaryFactory.flattenArtifacts(result.artifacts()).stream()
                .flatMap(artifact -> artifact.vulnerabilities().stream())
                .filter(finding -> !finding.suppressed() && finding.severity() == severity)
                .toList()
                .size();
    }

    private int integerConfig(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private int parseJavaFeature(String requiredJavaVersion) {
        if (requiredJavaVersion == null || requiredJavaVersion.isBlank()) {
            return 0;
        }
        String digits = requiredJavaVersion.replaceAll("[^0-9]", " ").trim();
        if (digits.isBlank()) {
            return 0;
        }
        String[] parts = digits.split("\\s+");
        try {
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String coordinate(String groupId, String artifactId, String version) {
        return (groupId == null ? "unknown" : groupId)
                + ":" + (artifactId == null ? "unknown" : artifactId)
                + ":" + (version == null ? "unknown" : version);
    }

    private List<PolicyEntity> builtIns(Instant now) {
        return List.of(
                new PolicyEntity("critical-vulnerabilities", "Fail if critical vulnerabilities exist", "Fails the scan review if unsuppressed critical vulnerabilities remain.", RULE_CRITICAL_VULNERABILITIES, PolicySeverity.FAIL, true, Map.of("threshold", 1), now, now),
                new PolicyEntity("high-vulnerabilities", "Warn if high vulnerabilities exist", "Warns when unsuppressed high vulnerabilities remain.", RULE_HIGH_VULNERABILITIES, PolicySeverity.WARN, true, Map.of("threshold", 1), now, now),
                new PolicyEntity("unknown-licenses", "Warn if unknown licenses exist", "Highlights components whose license metadata could not be classified with confidence.", RULE_UNKNOWN_LICENSES, PolicySeverity.WARN, true, Map.of("threshold", 1), now, now),
                new PolicyEntity("strong-copyleft", "Fail if GPL or AGPL style licenses exist", "Flags strong copyleft dependencies for distribution review.", RULE_STRONG_COPYLEFT, PolicySeverity.FAIL, true, Map.of("threshold", 1), now, now),
                new PolicyEntity("apparently-unused", "Warn if dependencies are apparently unused", "Encourages cleanup of dependencies that look removable based on current evidence.", RULE_APPARENTLY_UNUSED, PolicySeverity.WARN, true, Map.of("threshold", 1), now, now),
                new PolicyEntity("duplicate-classes", "Warn if duplicate classes exist", "Highlights classpath collisions that can lead to shadowing or non-deterministic behavior.", RULE_DUPLICATE_CLASSES, PolicySeverity.WARN, true, Map.of("threshold", 1), now, now),
                new PolicyEntity("multiple-versions", "Warn if multiple versions of the same artifact exist", "Highlights dependency drift and Maven nearest-wins style conflicts.", RULE_MULTIPLE_VERSIONS, PolicySeverity.WARN, true, Map.of("threshold", 1), now, now),
                new PolicyEntity("java-version-limit", "Warn if Java required version exceeds configured limit", "Compares the detected required Java version against a configurable ceiling.", RULE_JAVA_VERSION, PolicySeverity.WARN, true, Map.of("maxJavaFeature", 17), now, now),
                new PolicyEntity("snapshot-dependencies", "Warn if SNAPSHOT dependencies exist", "Highlights non-reproducible dependencies in the resolved graph or packaged set.", RULE_SNAPSHOT, PolicySeverity.WARN, true, Map.of("threshold", 1), now, now),
                new PolicyEntity("broad-bundle-dependency", "Warn if broad bundle dependencies exist", "Highlights bundle-style dependencies such as AWS SDK bundles that may hide unnecessary modules.", RULE_BROAD_BUNDLE, PolicySeverity.WARN, true, Map.of("threshold", 1), now, now)
        );
    }

    private PolicyRecord toRecord(PolicyEntity entity) {
        return new PolicyRecord(
                entity.id(),
                entity.name(),
                entity.description(),
                entity.ruleType(),
                entity.severity(),
                entity.enabled(),
                entity.config(),
                entity.createdAt(),
                entity.updatedAt()
        );
    }
}
