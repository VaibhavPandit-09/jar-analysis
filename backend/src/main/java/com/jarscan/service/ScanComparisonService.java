package com.jarscan.service;

import com.jarscan.dto.AnalysisResult;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.CountDiff;
import com.jarscan.dto.DependencyChangeItem;
import com.jarscan.dto.DependencyChangeType;
import com.jarscan.dto.DependencyComparisonSection;
import com.jarscan.dto.DoubleDiff;
import com.jarscan.dto.MavenCoordinates;
import com.jarscan.dto.ScanComparisonResponse;
import com.jarscan.dto.ScanComparisonSummaryDiff;
import com.jarscan.dto.StoredScanSummaryResponse;
import com.jarscan.dto.VulnerabilityChangeItem;
import com.jarscan.dto.VulnerabilityChangeType;
import com.jarscan.dto.VulnerabilityComparisonSection;
import com.jarscan.dto.VulnerabilityFinding;
import com.jarscan.persistence.PersistedScanRecord;
import com.jarscan.persistence.ScanHistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ScanComparisonService {

    private final ScanHistoryRepository repository;
    private final ScanHistoryService scanHistoryService;

    public ScanComparisonService(ScanHistoryRepository repository, ScanHistoryService scanHistoryService) {
        this.repository = repository;
        this.scanHistoryService = scanHistoryService;
    }

    public ScanComparisonResponse compareScans(String baseScanId, String targetScanId) {
        PersistedScanRecord baselineRecord = repository.findById(baseScanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown baseline scan: " + baseScanId));
        PersistedScanRecord targetRecord = repository.findById(targetScanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown target scan: " + targetScanId));

        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        AnalysisResult baselineResult = baselineRecord.result();
        AnalysisResult targetResult = targetRecord.result();
        if (baselineResult == null) {
            warnings.add("Baseline scan has no readable result JSON.");
        }
        if (targetResult == null) {
            warnings.add("Target scan has no readable result JSON.");
        }

        if (baselineResult == null || targetResult == null) {
            return new ScanComparisonResponse(
                    scanHistoryService.toSummaryResponse(baselineRecord),
                    scanHistoryService.toSummaryResponse(targetRecord),
                    buildSummaryDiff(scanHistoryService.toSummaryResponse(baselineRecord), scanHistoryService.toSummaryResponse(targetRecord)),
                    new DependencyComparisonSection(0, 0, 0, 0, List.of()),
                    new VulnerabilityComparisonSection(0, 0, 0, 0, List.of()),
                    warnings,
                    errors
            );
        }

        Map<String, ArtifactSnapshot> baselineArtifacts = flattenArtifacts(baselineResult.artifacts(), warnings, "baseline");
        Map<String, ArtifactSnapshot> targetArtifacts = flattenArtifacts(targetResult.artifacts(), warnings, "target");

        DependencyComparisonSection dependencyChanges = compareDependencies(baselineArtifacts, targetArtifacts);
        VulnerabilityComparisonSection vulnerabilityChanges = compareVulnerabilities(baselineArtifacts, targetArtifacts);

        StoredScanSummaryResponse baseSummary = scanHistoryService.toSummaryResponse(baselineRecord);
        StoredScanSummaryResponse targetSummary = scanHistoryService.toSummaryResponse(targetRecord);

        return new ScanComparisonResponse(
                baseSummary,
                targetSummary,
                buildSummaryDiff(baseSummary, targetSummary),
                dependencyChanges,
                vulnerabilityChanges,
                warnings,
                errors
        );
    }

    private ScanComparisonSummaryDiff buildSummaryDiff(StoredScanSummaryResponse before, StoredScanSummaryResponse after) {
        return new ScanComparisonSummaryDiff(
                countDiff(before.totalArtifacts(), after.totalArtifacts()),
                countDiff(before.totalDependencies(), after.totalDependencies()),
                countDiff(before.totalVulnerabilities(), after.totalVulnerabilities()),
                countDiff(before.criticalCount(), after.criticalCount()),
                countDiff(before.highCount(), after.highCount()),
                countDiff(before.mediumCount(), after.mediumCount()),
                countDiff(before.lowCount(), after.lowCount()),
                doubleDiff(before.highestCvss(), after.highestCvss()),
                null,
                null,
                null
        );
    }

    private CountDiff countDiff(int before, int after) {
        return new CountDiff(before, after, after - before);
    }

    private DoubleDiff doubleDiff(Double before, Double after) {
        if (before == null || after == null) {
            return new DoubleDiff(before, after, null);
        }
        return new DoubleDiff(before, after, after - before);
    }

    private DependencyComparisonSection compareDependencies(
            Map<String, ArtifactSnapshot> baselineArtifacts,
            Map<String, ArtifactSnapshot> targetArtifacts
    ) {
        List<DependencyChangeItem> items = new ArrayList<>();
        int added = 0;
        int removed = 0;
        int updated = 0;
        int unchanged = 0;

        for (Map.Entry<String, ArtifactSnapshot> entry : targetArtifacts.entrySet()) {
            String key = entry.getKey();
            ArtifactSnapshot target = entry.getValue();
            ArtifactSnapshot baseline = baselineArtifacts.get(key);
            if (baseline == null) {
                added++;
                items.add(toDependencyItem(DependencyChangeType.ADDED, key, null, target));
                continue;
            }
            boolean coordinatesChanged = !Objects.equals(baseline.groupId, target.groupId)
                    || !Objects.equals(baseline.artifactId, target.artifactId);
            boolean versionChanged = !Objects.equals(baseline.version, target.version);
            boolean javaChanged = !Objects.equals(baseline.javaVersion, target.javaVersion);
            boolean vulnerabilityChanged = baseline.vulnerabilityCount != target.vulnerabilityCount;
            if (coordinatesChanged || versionChanged || javaChanged || vulnerabilityChanged) {
                updated++;
                items.add(toDependencyItem(DependencyChangeType.UPDATED, key, baseline, target));
            } else {
                unchanged++;
                items.add(toDependencyItem(DependencyChangeType.UNCHANGED, key, baseline, target));
            }
        }

        for (Map.Entry<String, ArtifactSnapshot> entry : baselineArtifacts.entrySet()) {
            if (!targetArtifacts.containsKey(entry.getKey())) {
                removed++;
                items.add(toDependencyItem(DependencyChangeType.REMOVED, entry.getKey(), entry.getValue(), null));
            }
        }

        items.sort(Comparator.comparing(DependencyChangeItem::artifactKey));
        return new DependencyComparisonSection(added, removed, updated, unchanged, items);
    }

    private DependencyChangeItem toDependencyItem(DependencyChangeType type, String key, ArtifactSnapshot before, ArtifactSnapshot after) {
        boolean coordinatesChanged = before != null && after != null
                && (!Objects.equals(before.groupId, after.groupId) || !Objects.equals(before.artifactId, after.artifactId));
        boolean versionChanged = before != null && after != null && !Objects.equals(before.version, after.version);
        boolean javaVersionChanged = before != null && after != null && !Objects.equals(before.javaVersion, after.javaVersion);
        boolean vulnerabilityCountChanged = before != null && after != null && before.vulnerabilityCount != after.vulnerabilityCount;
        return new DependencyChangeItem(
                type,
                key,
                before == null ? null : before.groupId,
                before == null ? null : before.artifactId,
                after == null ? null : after.groupId,
                after == null ? null : after.artifactId,
                before == null ? null : before.version,
                after == null ? null : after.version,
                before == null ? null : before.javaVersion,
                after == null ? null : after.javaVersion,
                before == null ? null : before.vulnerabilityCount,
                after == null ? null : after.vulnerabilityCount,
                after != null ? after.scope : (before == null ? null : before.scope),
                coordinatesChanged,
                versionChanged,
                javaVersionChanged,
                vulnerabilityCountChanged
        );
    }

    private VulnerabilityComparisonSection compareVulnerabilities(
            Map<String, ArtifactSnapshot> baselineArtifacts,
            Map<String, ArtifactSnapshot> targetArtifacts
    ) {
        Map<String, VulnerabilitySnapshot> baseline = flattenVulnerabilities(baselineArtifacts);
        Map<String, VulnerabilitySnapshot> target = flattenVulnerabilities(targetArtifacts);
        List<VulnerabilityChangeItem> items = new ArrayList<>();
        int created = 0;
        int fixed = 0;
        int changed = 0;
        int unchanged = 0;

        for (Map.Entry<String, VulnerabilitySnapshot> entry : target.entrySet()) {
            VulnerabilitySnapshot next = entry.getValue();
            VulnerabilitySnapshot previous = baseline.get(entry.getKey());
            if (previous == null) {
                created++;
                items.add(toVulnerabilityItem(VulnerabilityChangeType.NEW, null, next));
                continue;
            }
            boolean severityChanged = previous.finding.severity() != next.finding.severity();
            boolean cvssChanged = !Objects.equals(previous.finding.cvssScore(), next.finding.cvssScore());
            boolean dependencyVersionChanged = !Objects.equals(previous.artifact.version, next.artifact.version);
            if (severityChanged || cvssChanged || dependencyVersionChanged) {
                changed++;
                items.add(toVulnerabilityItem(VulnerabilityChangeType.CHANGED, previous, next));
            } else {
                unchanged++;
                items.add(toVulnerabilityItem(VulnerabilityChangeType.UNCHANGED, previous, next));
            }
        }
        for (Map.Entry<String, VulnerabilitySnapshot> entry : baseline.entrySet()) {
            if (!target.containsKey(entry.getKey())) {
                fixed++;
                items.add(toVulnerabilityItem(VulnerabilityChangeType.FIXED, entry.getValue(), null));
            }
        }
        items.sort(Comparator.comparing(VulnerabilityChangeItem::vulnerabilityId));
        return new VulnerabilityComparisonSection(created, fixed, changed, unchanged, items);
    }

    private VulnerabilityChangeItem toVulnerabilityItem(
            VulnerabilityChangeType type,
            VulnerabilitySnapshot before,
            VulnerabilitySnapshot after
    ) {
        VulnerabilitySnapshot source = Optional.ofNullable(after).orElse(before);
        return new VulnerabilityChangeItem(
                type,
                source.identity,
                source.finding.cveId(),
                before == null ? null : before.finding.severity(),
                after == null ? null : after.finding.severity(),
                before == null ? null : before.finding.cvssScore(),
                after == null ? null : after.finding.cvssScore(),
                source.artifact.identityKey,
                source.artifact.groupId,
                source.artifact.artifactId,
                before == null ? null : before.artifact.version,
                after == null ? null : after.artifact.version
        );
    }

    private Map<String, VulnerabilitySnapshot> flattenVulnerabilities(Map<String, ArtifactSnapshot> artifacts) {
        Map<String, VulnerabilitySnapshot> flattened = new LinkedHashMap<>();
        for (ArtifactSnapshot artifact : artifacts.values()) {
            for (VulnerabilityFinding finding : artifact.vulnerabilities) {
                String vulnIdentity = vulnerabilityIdentity(finding);
                String identity = vulnIdentity + "|" + artifact.coordinateKey;
                flattened.putIfAbsent(identity, new VulnerabilitySnapshot(identity, finding, artifact));
            }
        }
        return flattened;
    }

    private String vulnerabilityIdentity(VulnerabilityFinding finding) {
        if (finding.cveId() != null && !finding.cveId().isBlank()) {
            return finding.cveId();
        }
        if (finding.source() != null && !finding.source().isBlank()) {
            return finding.source();
        }
        if (finding.packageName() != null && !finding.packageName().isBlank()) {
            return finding.packageName();
        }
        return "unknown-vulnerability";
    }

    private Map<String, ArtifactSnapshot> flattenArtifacts(List<ArtifactAnalysis> artifacts, List<String> warnings, String label) {
        Map<String, ArtifactSnapshot> flattened = new LinkedHashMap<>();
        if (artifacts == null) {
            return flattened;
        }
        for (ArtifactAnalysis artifact : artifacts) {
            flattenArtifactRecursive(artifact, flattened, warnings, label);
        }
        return flattened;
    }

    private void flattenArtifactRecursive(
            ArtifactAnalysis artifact,
            Map<String, ArtifactSnapshot> flattened,
            List<String> warnings,
            String label
    ) {
        ArtifactSnapshot snapshot = toSnapshot(artifact);
        ArtifactSnapshot existing = flattened.putIfAbsent(snapshot.identityKey, snapshot);
        if (existing != null) {
            warnings.add("Duplicate " + label + " artifact identity encountered: " + snapshot.identityKey);
        }
        if (artifact.nestedArtifacts() == null) {
            return;
        }
        for (ArtifactAnalysis nested : artifact.nestedArtifacts()) {
            flattenArtifactRecursive(nested, flattened, warnings, label);
        }
    }

    private ArtifactSnapshot toSnapshot(ArtifactAnalysis artifact) {
        String groupId = artifact.coordinates() == null ? null : artifact.coordinates().groupId();
        String artifactId = artifact.coordinates() == null ? null : artifact.coordinates().artifactId();
        String version = artifact.coordinates() == null ? null : artifact.coordinates().version();
        String coordinateKey = coordinateKey(artifact.coordinates(), artifact.fileName(), artifact.sha256(), artifact.id());
        String identityKey = identityKey(artifact.coordinates(), artifact.fileName(), artifact.sha256(), artifact.id());
        String javaVersion = artifact.javaVersion() == null ? null : artifact.javaVersion().requiredJava();
        @SuppressWarnings("unchecked")
        String scope = artifact.rawMetadata() != null && artifact.rawMetadata().get("scope") instanceof String
                ? (String) artifact.rawMetadata().get("scope")
                : null;
        return new ArtifactSnapshot(
                identityKey,
                coordinateKey,
                groupId,
                artifactId,
                version,
                javaVersion,
                scope,
                artifact.vulnerabilityCount(),
                artifact.vulnerabilities() == null ? List.of() : artifact.vulnerabilities()
        );
    }

    private String identityKey(MavenCoordinates coordinates, String fileName, String sha256, String artifactId) {
        if (coordinates != null && coordinates.groupId() != null && coordinates.artifactId() != null) {
            return coordinates.groupId() + ":" + coordinates.artifactId();
        }
        if (fileName != null && !fileName.isBlank()) {
            return "file:" + fileName;
        }
        if (sha256 != null && !sha256.isBlank()) {
            return "sha256:" + sha256;
        }
        return "artifact:" + artifactId;
    }

    private String coordinateKey(MavenCoordinates coordinates, String fileName, String sha256, String artifactId) {
        if (coordinates != null && coordinates.groupId() != null && coordinates.artifactId() != null) {
            return coordinates.groupId() + ":" + coordinates.artifactId();
        }
        return identityKey(coordinates, fileName, sha256, artifactId);
    }

    private record ArtifactSnapshot(
            String identityKey,
            String coordinateKey,
            String groupId,
            String artifactId,
            String version,
            String javaVersion,
            String scope,
            int vulnerabilityCount,
            List<VulnerabilityFinding> vulnerabilities
    ) {
    }

    private record VulnerabilitySnapshot(
            String identity,
            VulnerabilityFinding finding,
            ArtifactSnapshot artifact
    ) {
    }
}
