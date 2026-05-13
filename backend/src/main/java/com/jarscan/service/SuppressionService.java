package com.jarscan.service;

import com.jarscan.dto.AnalysisResult;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.CreateSuppressionRequest;
import com.jarscan.dto.DependencyUsageFinding;
import com.jarscan.dto.DuplicateClassFinding;
import com.jarscan.dto.LicenseFinding;
import com.jarscan.dto.PolicyEvaluation;
import com.jarscan.dto.PolicyFinding;
import com.jarscan.dto.SuppressionRecord;
import com.jarscan.dto.UpdateSuppressionRequest;
import com.jarscan.dto.VersionConflictFinding;
import com.jarscan.dto.ConvergenceFinding;
import com.jarscan.dto.VulnerabilityFinding;
import com.jarscan.model.SuppressionType;
import com.jarscan.persistence.SuppressionEntity;
import com.jarscan.persistence.SuppressionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SuppressionService {

    private final SuppressionRepository repository;

    public SuppressionService(SuppressionRepository repository) {
        this.repository = repository;
    }

    public List<SuppressionRecord> listSuppressions() {
        return repository.findAll().stream().map(this::toRecord).toList();
    }

    public SuppressionRecord createSuppression(CreateSuppressionRequest request) {
        Instant now = Instant.now();
        SuppressionEntity entity = new SuppressionEntity(
                UUID.randomUUID().toString(),
                request.type(),
                normalize(request.groupId()),
                normalize(request.artifactId()),
                normalize(request.version()),
                normalize(request.cveId()),
                request.reason().trim(),
                request.expiresAt(),
                request.active() == null || request.active(),
                now,
                now
        );
        repository.insert(entity);
        return toRecord(entity);
    }

    public SuppressionRecord updateSuppression(String id, UpdateSuppressionRequest request) {
        SuppressionEntity existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown suppression: " + id));
        SuppressionEntity updated = new SuppressionEntity(
                existing.id(),
                existing.type(),
                request.groupId() == null ? existing.groupId() : normalize(request.groupId()),
                request.artifactId() == null ? existing.artifactId() : normalize(request.artifactId()),
                request.version() == null ? existing.version() : normalize(request.version()),
                request.cveId() == null ? existing.cveId() : normalize(request.cveId()),
                request.reason() == null ? existing.reason() : request.reason().trim(),
                request.expiresAt() == null ? existing.expiresAt() : request.expiresAt(),
                request.active() == null ? existing.active() : request.active(),
                existing.createdAt(),
                Instant.now()
        );
        return toRecord(repository.update(updated)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown suppression: " + id)));
    }

    public void deleteSuppression(String id) {
        if (!repository.deleteById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown suppression: " + id);
        }
    }

    public AnalysisResult applyDomainSuppressions(AnalysisResult result) {
        List<SuppressionEntity> active = repository.findActive(Instant.now());
        if (active.isEmpty()) {
            return result;
        }

        List<ArtifactAnalysis> artifacts = result.artifacts().stream().map(artifact -> applyArtifactSuppressions(artifact, active)).toList();
        List<VersionConflictFinding> versionConflicts = result.versionConflicts().stream()
                .map(finding -> applyVersionConflictSuppression(finding, active, false))
                .toList();
        List<ConvergenceFinding> convergenceFindings = result.convergenceFindings().stream()
                .map(finding -> applyConvergenceSuppression(finding, active))
                .toList();
        List<DuplicateClassFinding> duplicateClasses = result.duplicateClasses().stream()
                .map(finding -> applyDuplicateClassSuppression(finding, active))
                .toList();
        List<LicenseFinding> licenses = result.licenses().stream()
                .map(finding -> applyLicenseSuppression(finding, active))
                .toList();
        List<DependencyUsageFinding> dependencyUsage = result.dependencyUsage().stream()
                .map(finding -> applyUsageSuppression(finding, active))
                .toList();

        return new AnalysisResult(
                result.jobId(),
                result.status(),
                result.inputType(),
                result.startedAt(),
                result.completedAt(),
                result.summary(),
                artifacts,
                result.dependencyTree(),
                versionConflicts,
                convergenceFindings,
                duplicateClasses,
                licenses,
                dependencyUsage,
                result.slimmingOpportunities(),
                result.awsBundleAdvice(),
                result.policyEvaluation(),
                result.dependencyTreeText(),
                result.warnings(),
                result.errors(),
                result.projectStructure()
        );
    }

    public AnalysisResult applyPolicySuppressions(AnalysisResult result) {
        if (result.policyEvaluation() == null) {
            return result;
        }
        List<SuppressionEntity> active = repository.findActive(Instant.now());
        if (active.isEmpty()) {
            return result;
        }
        List<PolicyFinding> findings = result.policyEvaluation().findings().stream()
                .map(finding -> applyPolicySuppression(finding, active))
                .toList();
        PolicyEvaluation evaluation = new PolicyEvaluation(
                result.policyEvaluation().overallStatus(),
                result.policyEvaluation().passedCount(),
                result.policyEvaluation().warningCount(),
                result.policyEvaluation().failedCount(),
                findings
        );
        return new AnalysisResult(
                result.jobId(),
                result.status(),
                result.inputType(),
                result.startedAt(),
                result.completedAt(),
                result.summary(),
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

    private ArtifactAnalysis applyArtifactSuppressions(ArtifactAnalysis artifact, List<SuppressionEntity> active) {
        List<VulnerabilityFinding> vulnerabilities = artifact.vulnerabilities().stream()
                .map(finding -> applyVulnerabilitySuppression(artifact, finding, active))
                .toList();
        List<ArtifactAnalysis> nested = artifact.nestedArtifacts().stream()
                .map(next -> applyArtifactSuppressions(next, active))
                .toList();
        return new ArtifactAnalysis(
                artifact.id(),
                artifact.fileName(),
                artifact.sizeBytes(),
                artifact.sha256(),
                artifact.entryCount(),
                artifact.fatJar(),
                artifact.parentPath(),
                artifact.nestedDepth(),
                artifact.coordinates(),
                artifact.javaVersion(),
                artifact.manifest(),
                artifact.moduleType(),
                artifact.highestSeverity(),
                artifact.vulnerabilityCount(),
                artifact.dependencies(),
                vulnerabilities,
                nested,
                artifact.rawMetadata(),
                artifact.packagingInspection()
        );
    }

    private VulnerabilityFinding applyVulnerabilitySuppression(ArtifactAnalysis artifact, VulnerabilityFinding finding, List<SuppressionEntity> active) {
        SuppressionEntity suppression = active.stream()
                .filter(item -> item.type() == SuppressionType.VULNERABILITY)
                .filter(item -> matches(item.groupId(), artifact.coordinates().groupId()))
                .filter(item -> matches(item.artifactId(), artifact.coordinates().artifactId()))
                .filter(item -> matches(item.version(), artifact.coordinates().version()) || matches(item.version(), finding.installedVersion()))
                .filter(item -> matches(item.cveId(), finding.cveId()))
                .findFirst()
                .orElse(null);
        if (suppression == null) {
            return finding;
        }
        return new VulnerabilityFinding(
                finding.severity(),
                finding.cveId(),
                finding.cvssScore(),
                finding.packageName(),
                finding.installedVersion(),
                finding.affectedVersionRange(),
                finding.description(),
                finding.references(),
                finding.source(),
                true,
                suppression.reason(),
                suppression.expiresAt()
        );
    }

    private LicenseFinding applyLicenseSuppression(LicenseFinding finding, List<SuppressionEntity> active) {
        SuppressionEntity suppression = active.stream()
                .filter(item -> item.type() == SuppressionType.LICENSE)
                .filter(item -> matches(item.groupId(), finding.groupId()))
                .filter(item -> matches(item.artifactId(), finding.artifactId()))
                .filter(item -> matches(item.version(), finding.version()))
                .findFirst()
                .orElse(null);
        if (suppression == null) {
            return finding;
        }
        return new LicenseFinding(
                finding.groupId(),
                finding.artifactId(),
                finding.version(),
                finding.licenseName(),
                finding.licenseUrl(),
                finding.source(),
                finding.confidence(),
                finding.category(),
                finding.warnings(),
                true,
                suppression.reason(),
                suppression.expiresAt()
        );
    }

    private VersionConflictFinding applyVersionConflictSuppression(VersionConflictFinding finding, List<SuppressionEntity> active, boolean convergence) {
        SuppressionEntity suppression = active.stream()
                .filter(item -> item.type() == SuppressionType.VERSION_CONFLICT)
                .filter(item -> matches(item.groupId(), finding.groupId()))
                .filter(item -> matches(item.artifactId(), finding.artifactId()))
                .filter(item -> item.version() == null || finding.requestedVersions().contains(item.version()) || matches(item.version(), finding.resolvedVersion()))
                .findFirst()
                .orElse(null);
        if (suppression == null) {
            return finding;
        }
        return new VersionConflictFinding(
                finding.groupId(),
                finding.artifactId(),
                finding.resolvedVersion(),
                finding.requestedVersions(),
                finding.pathsByVersion(),
                finding.conflictType(),
                finding.riskLevel(),
                finding.recommendation(),
                finding.dependencyManagementSnippet(),
                true,
                suppression.reason(),
                suppression.expiresAt()
        );
    }

    private ConvergenceFinding applyConvergenceSuppression(ConvergenceFinding finding, List<SuppressionEntity> active) {
        SuppressionEntity suppression = active.stream()
                .filter(item -> item.type() == SuppressionType.VERSION_CONFLICT)
                .filter(item -> matches(item.groupId(), finding.groupId()))
                .filter(item -> matches(item.artifactId(), finding.artifactId()))
                .filter(item -> item.version() == null || finding.versionsFound().contains(item.version()) || matches(item.version(), finding.selectedVersion()))
                .findFirst()
                .orElse(null);
        if (suppression == null) {
            return finding;
        }
        return new ConvergenceFinding(
                finding.groupId(),
                finding.artifactId(),
                finding.versionsFound(),
                finding.pathsByVersion(),
                finding.selectedVersion(),
                finding.recommendation(),
                finding.snippet(),
                true,
                suppression.reason(),
                suppression.expiresAt()
        );
    }

    private DuplicateClassFinding applyDuplicateClassSuppression(DuplicateClassFinding finding, List<SuppressionEntity> active) {
        SuppressionEntity suppression = active.stream()
                .filter(item -> item.type() == SuppressionType.DUPLICATE_CLASS)
                .filter(item -> matches(item.groupId(), finding.findingType()))
                .filter(item -> matches(item.artifactId(), finding.symbol()))
                .findFirst()
                .orElse(null);
        if (suppression == null) {
            return finding;
        }
        return new DuplicateClassFinding(
                finding.findingType(),
                finding.symbol(),
                finding.packageName(),
                finding.artifacts(),
                finding.severity(),
                finding.recommendation(),
                finding.shadowingWarning(),
                true,
                suppression.reason(),
                suppression.expiresAt()
        );
    }

    private DependencyUsageFinding applyUsageSuppression(DependencyUsageFinding finding, List<SuppressionEntity> active) {
        SuppressionEntity suppression = active.stream()
                .filter(item -> item.type() == SuppressionType.USAGE || item.type() == SuppressionType.DEPENDENCY)
                .filter(item -> matches(item.groupId(), finding.groupId()))
                .filter(item -> matches(item.artifactId(), finding.artifactId()))
                .filter(item -> matches(item.version(), finding.version()))
                .findFirst()
                .orElse(null);
        if (suppression == null) {
            return finding;
        }
        return new DependencyUsageFinding(
                finding.groupId(),
                finding.artifactId(),
                finding.version(),
                finding.status(),
                finding.confidence(),
                finding.evidence(),
                finding.warnings(),
                finding.suggestedAction(),
                finding.paths(),
                finding.sizeBytes(),
                finding.vulnerabilitiesContributed(),
                true,
                suppression.reason(),
                suppression.expiresAt()
        );
    }

    private PolicyFinding applyPolicySuppression(PolicyFinding finding, List<SuppressionEntity> active) {
        SuppressionEntity suppression = active.stream()
                .filter(item -> item.type() == SuppressionType.POLICY)
                .filter(item -> matches(item.groupId(), finding.policyId()) || matches(item.groupId(), finding.ruleType()))
                .filter(item -> item.artifactId() == null || finding.affectedDependencies().contains(item.artifactId()))
                .findFirst()
                .orElse(null);
        if (suppression == null) {
            return finding;
        }
        return new PolicyFinding(
                finding.policyId(),
                finding.policyName(),
                finding.ruleType(),
                finding.status(),
                finding.severity(),
                finding.message(),
                finding.affectedDependencies(),
                finding.recommendation(),
                true,
                suppression.reason(),
                suppression.expiresAt()
        );
    }

    private boolean matches(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SuppressionRecord toRecord(SuppressionEntity entity) {
        return new SuppressionRecord(
                entity.id(),
                entity.type(),
                entity.groupId(),
                entity.artifactId(),
                entity.version(),
                entity.cveId(),
                entity.reason(),
                entity.expiresAt(),
                entity.active(),
                entity.createdAt(),
                entity.updatedAt()
        );
    }
}
