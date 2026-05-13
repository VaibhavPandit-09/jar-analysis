package com.jarscan.service;

import com.jarscan.dto.ConvergenceFinding;
import com.jarscan.dto.DependencyTree;
import com.jarscan.dto.DependencyTreeNode;
import com.jarscan.dto.VersionConflictFinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DependencyConflictAnalysisService {

    private static final Pattern VERSION_MANAGED_PATTERN = Pattern.compile("version managed from ([^),]+)", Pattern.CASE_INSENSITIVE);

    public DependencyConflictAnalysisResult analyze(DependencyTree dependencyTree) {
        if (dependencyTree == null || dependencyTree.roots() == null || dependencyTree.roots().isEmpty()) {
            return new DependencyConflictAnalysisResult(List.of(), List.of());
        }

        Map<String, List<NodeObservation>> observationsByDependency = new LinkedHashMap<>();
        for (DependencyTreeNode root : dependencyTree.roots()) {
            collect(root, observationsByDependency);
        }

        List<VersionConflictFinding> conflicts = new ArrayList<>();
        List<ConvergenceFinding> convergenceFindings = new ArrayList<>();

        for (Map.Entry<String, List<NodeObservation>> entry : observationsByDependency.entrySet()) {
            List<NodeObservation> observations = entry.getValue();
            if (observations.isEmpty()) {
                continue;
            }
            DependencyTreeNode representative = observations.getFirst().node();
            LinkedHashSet<String> requestedVersions = new LinkedHashSet<>();
            Map<String, List<List<String>>> pathsByVersion = new LinkedHashMap<>();
            boolean hasConflict = false;
            boolean hasDuplicate = false;
            boolean hasManagedOverride = false;
            boolean nearestWins = false;

            String resolvedVersion = observations.stream()
                    .map(NodeObservation::node)
                    .filter(node -> !node.omitted())
                    .map(DependencyTreeNode::version)
                    .filter(version -> version != null && !version.isBlank())
                    .findFirst()
                    .orElse(representative.version());

            int resolvedDepth = observations.stream()
                    .map(NodeObservation::node)
                    .filter(node -> resolvedVersion != null && resolvedVersion.equals(node.version()) && !node.omitted())
                    .mapToInt(DependencyTreeNode::depth)
                    .min()
                    .orElse(Integer.MAX_VALUE);

            for (NodeObservation observation : observations) {
                DependencyTreeNode node = observation.node();
                if (node.version() != null && !node.version().isBlank()) {
                    requestedVersions.add(node.version());
                    pathsByVersion.computeIfAbsent(node.version(), key -> new ArrayList<>()).add(node.pathFromRoot());
                }
                if (observation.versionManagedFrom() != null && !observation.versionManagedFrom().isBlank()) {
                    requestedVersions.add(observation.versionManagedFrom());
                    pathsByVersion.computeIfAbsent(observation.versionManagedFrom(), key -> new ArrayList<>()).add(node.pathFromRoot());
                    hasManagedOverride = true;
                }
                hasConflict = hasConflict || node.conflict();
                hasDuplicate = hasDuplicate || (node.omittedReason() != null && node.omittedReason().toLowerCase(Locale.ROOT).contains("duplicate"));
                nearestWins = nearestWins || (node.conflict() && node.depth() > resolvedDepth);
            }

            if (requestedVersions.size() > 1) {
                convergenceFindings.add(new ConvergenceFinding(
                        representative.groupId(),
                        representative.artifactId(),
                        List.copyOf(requestedVersions),
                        immutablePaths(pathsByVersion),
                        resolvedVersion,
                        "Align all incoming paths to a single version so the graph converges cleanly across modules and packaging styles.",
                        dependencyManagementSnippet(representative.groupId(), representative.artifactId(), resolvedVersion)
                ));
            }

            if (requestedVersions.size() > 1 || hasConflict || hasDuplicate || hasManagedOverride) {
                conflicts.add(new VersionConflictFinding(
                        representative.groupId(),
                        representative.artifactId(),
                        resolvedVersion,
                        List.copyOf(requestedVersions),
                        immutablePaths(pathsByVersion),
                        conflictType(hasManagedOverride, hasConflict, hasDuplicate, nearestWins),
                        riskLevel(requestedVersions, hasConflict, nearestWins, observations),
                        recommendation(hasManagedOverride, hasConflict, hasDuplicate, nearestWins),
                        dependencyManagementSnippet(representative.groupId(), representative.artifactId(), resolvedVersion)
                ));
            }
        }

        conflicts.sort((left, right) -> compareByRisk(left.riskLevel(), right.riskLevel(), left.groupId() + ":" + left.artifactId(), right.groupId() + ":" + right.artifactId()));
        convergenceFindings.sort((left, right) -> (left.groupId() + ":" + left.artifactId()).compareTo(right.groupId() + ":" + right.artifactId()));
        return new DependencyConflictAnalysisResult(List.copyOf(conflicts), List.copyOf(convergenceFindings));
    }

    String dependencyManagementSnippet(String groupId, String artifactId, String version) {
        if (groupId == null || artifactId == null || version == null || version.isBlank()) {
            return null;
        }
        return """
                <dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>%s</groupId>
                      <artifactId>%s</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </dependencyManagement>
                """.formatted(groupId, artifactId, version);
    }

    private int compareByRisk(String leftRisk, String rightRisk, String leftKey, String rightKey) {
        int riskOrder = Integer.compare(riskRank(rightRisk), riskRank(leftRisk));
        if (riskOrder != 0) {
            return riskOrder;
        }
        return leftKey.compareTo(rightKey);
    }

    private int riskRank(String risk) {
        return switch (risk == null ? "LOW" : risk) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private void collect(DependencyTreeNode node, Map<String, List<NodeObservation>> observationsByDependency) {
        if (node.depth() > 0 && node.groupId() != null && node.artifactId() != null) {
            observationsByDependency
                    .computeIfAbsent(node.groupId() + ":" + node.artifactId(), key -> new ArrayList<>())
                    .add(new NodeObservation(node, extractManagedFrom(node.rawLine())));
        }
        node.children().forEach(child -> collect(child, observationsByDependency));
    }

    private String extractManagedFrom(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        Matcher matcher = VERSION_MANAGED_PATTERN.matcher(rawLine);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private Map<String, List<List<String>>> immutablePaths(Map<String, List<List<String>>> input) {
        Map<String, List<List<String>>> output = new LinkedHashMap<>();
        input.forEach((version, paths) -> output.put(version, List.copyOf(paths)));
        return Map.copyOf(output);
    }

    private String conflictType(boolean hasManagedOverride, boolean hasConflict, boolean hasDuplicate, boolean nearestWins) {
        if (hasManagedOverride) {
            return "DEPENDENCY_MANAGEMENT_OVERRIDE";
        }
        if (nearestWins || hasConflict) {
            return "NEAREST_WINS_CONFLICT";
        }
        if (hasDuplicate) {
            return "DUPLICATE_DECLARATION";
        }
        return "MULTIPLE_PATHS";
    }

    private String riskLevel(LinkedHashSet<String> versions, boolean hasConflict, boolean nearestWins, List<NodeObservation> observations) {
        if ((hasConflict || nearestWins) && hasMajorVersionGap(versions)) {
            return "HIGH";
        }
        boolean directDependencyInvolved = observations.stream().anyMatch(observation -> observation.node().direct());
        if (hasConflict || versions.size() > 2 || (versions.size() > 1 && directDependencyInvolved)) {
            return "HIGH";
        }
        if (versions.size() > 1) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean hasMajorVersionGap(LinkedHashSet<String> versions) {
        Integer min = null;
        Integer max = null;
        for (String version : versions) {
            Integer major = parseMajor(version);
            if (major == null) {
                continue;
            }
            min = min == null ? major : Math.min(min, major);
            max = max == null ? major : Math.max(max, major);
        }
        return min != null && max != null && max - min >= 1;
    }

    private Integer parseMajor(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        int index = 0;
        while (index < version.length() && Character.isDigit(version.charAt(index))) {
            index++;
        }
        if (index == 0) {
            return null;
        }
        try {
            return Integer.parseInt(version.substring(0, index));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String recommendation(boolean hasManagedOverride, boolean hasConflict, boolean hasDuplicate, boolean nearestWins) {
        if (hasManagedOverride) {
            return "Keep the selected version explicit in dependencyManagement and verify that downstream declarations no longer request older versions.";
        }
        if (nearestWins || hasConflict) {
            return "Review the introducing paths, then align transitive versions so Maven does not rely on nearest-wins conflict selection.";
        }
        if (hasDuplicate) {
            return "Remove redundant declarations or exclusions so the same dependency is not requested multiple times without adding value.";
        }
        return "Review repeated paths for this dependency and keep only the intentional graph shape.";
    }

    private record NodeObservation(DependencyTreeNode node, String versionManagedFrom) {
    }

    public record DependencyConflictAnalysisResult(
            List<VersionConflictFinding> versionConflicts,
            List<ConvergenceFinding> convergenceFindings
    ) {
    }
}
