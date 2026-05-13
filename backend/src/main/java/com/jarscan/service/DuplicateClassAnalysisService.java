package com.jarscan.service;

import com.jarscan.config.JarScanProperties;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.DuplicateClassFinding;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

@Service
public class DuplicateClassAnalysisService {

    private static final int MAX_CLASS_ENTRIES = 200_000;
    private static final int MAX_FINDINGS = 250;

    private final JarScanProperties properties;

    public DuplicateClassAnalysisService(JarScanProperties properties) {
        this.properties = properties;
    }

    public List<DuplicateClassFinding> analyze(List<ArtifactAnalysis> flattenedArtifacts, List<String> warnings) {
        List<ArtifactAnalysis> scannable = flattenedArtifacts.stream()
                .filter(artifact -> sourcePath(artifact) != null)
                .sorted(Comparator.comparingInt(ArtifactAnalysis::nestedDepth).thenComparing(ArtifactAnalysis::fileName))
                .toList();
        if (scannable.isEmpty()) {
            return List.of();
        }

        if (scannable.size() > properties.maxDuplicateClassScanJars()) {
            warnings.add("Duplicate class scan limited to the first " + properties.maxDuplicateClassScanJars() + " archives out of " + scannable.size() + ".");
            scannable = scannable.subList(0, properties.maxDuplicateClassScanJars());
        }

        Map<String, Set<String>> classOwners = new LinkedHashMap<>();
        Map<String, Set<String>> packageOwners = new LinkedHashMap<>();
        int classEntryCount = 0;
        boolean truncated = false;
        for (ArtifactAnalysis artifact : scannable) {
            Path path = Path.of(sourcePath(artifact));
            try (JarFile jarFile = new JarFile(path.toFile())) {
                var entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                        continue;
                    }
                    classEntryCount++;
                    if (classEntryCount > MAX_CLASS_ENTRIES) {
                        warnings.add("Duplicate class scan stopped after " + MAX_CLASS_ENTRIES + " class entries to avoid excessive cost.");
                        truncated = true;
                        break;
                    }
                    String className = entry.getName().replace('/', '.');
                    String packageName = packageName(className);
                    String owner = artifactLabel(artifact);
                    classOwners.computeIfAbsent(className, key -> new LinkedHashSet<>()).add(owner);
                    if (packageName != null) {
                        packageOwners.computeIfAbsent(packageName, key -> new LinkedHashSet<>()).add(owner);
                    }
                }
            } catch (IOException ex) {
                warnings.add("Unable to inspect classes in " + artifact.fileName() + ": " + ex.getMessage());
            }
            if (truncated) {
                break;
            }
        }

        List<DuplicateClassFinding> findings = new ArrayList<>();
        classOwners.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .sorted(Map.Entry.comparingByKey())
                .limit(MAX_FINDINGS)
                .forEach(entry -> findings.add(new DuplicateClassFinding(
                        "EXACT_DUPLICATE_CLASS",
                        entry.getKey(),
                        packageName(entry.getKey()),
                        List.copyOf(entry.getValue()),
                        entry.getValue().size() > 2 ? "HIGH" : "MEDIUM",
                        "Keep only one provider of this class on the runtime path or exclude the duplicate from one side of the graph.",
                        "Classpath shadowing can make runtime behavior depend on archive ordering instead of your intended dependency selection."
                )));

        packageOwners.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .filter(entry -> findings.stream().noneMatch(finding -> entry.getKey().equals(finding.packageName())))
                .sorted(Map.Entry.comparingByKey())
                .limit(Math.max(0, MAX_FINDINGS - findings.size()))
                .forEach(entry -> findings.add(new DuplicateClassFinding(
                        "SPLIT_PACKAGE",
                        entry.getKey(),
                        entry.getKey(),
                        List.copyOf(entry.getValue()),
                        "LOW",
                        "Review whether this split package layout is intentional, especially if the archives will be combined into one classpath or module path.",
                        "Split packages raise the chance of classpath ambiguity and can complicate modular Java deployments."
                )));

        findings.addAll(patternFindings(scannable));
        if (findings.size() > MAX_FINDINGS) {
            warnings.add("Duplicate class findings were capped at " + MAX_FINDINGS + " entries.");
            return List.copyOf(findings.subList(0, MAX_FINDINGS));
        }
        return List.copyOf(findings);
    }

    private List<DuplicateClassFinding> patternFindings(List<ArtifactAnalysis> artifacts) {
        List<DuplicateClassFinding> findings = new ArrayList<>();
        List<ArtifactAnalysis> bindings = artifacts.stream()
                .filter(artifact -> {
                    String artifactId = artifact.coordinates().artifactId();
                    return artifactId != null && List.of("logback-classic", "slf4j-simple", "slf4j-jdk14", "slf4j-log4j12", "log4j-slf4j-impl", "log4j-slf4j2-impl").contains(artifactId);
                })
                .toList();
        if (bindings.size() > 1) {
            findings.add(patternFinding("PATTERN_MULTIPLE_SLF4J_BINDINGS", "Multiple SLF4J bindings", bindings, "HIGH",
                    "Keep a single SLF4J binding at runtime so logging does not become non-deterministic or noisy."));
        }
        addVersionPattern(findings, artifacts, "Multiple Jackson versions", artifact -> artifact.coordinates().groupId() != null && artifact.coordinates().groupId().startsWith("com.fasterxml.jackson"), "MEDIUM");
        addVersionPattern(findings, artifacts, "Multiple Netty versions", artifact -> "io.netty".equals(artifact.coordinates().groupId()), "MEDIUM");
        addVersionPattern(findings, artifacts, "Multiple Guava versions", artifact -> "com.google.guava".equals(artifact.coordinates().groupId()) && "guava".equals(artifact.coordinates().artifactId()), "MEDIUM");
        return findings;
    }

    private void addVersionPattern(
            List<DuplicateClassFinding> findings,
            List<ArtifactAnalysis> artifacts,
            String label,
            java.util.function.Predicate<ArtifactAnalysis> predicate,
            String severity
    ) {
        List<ArtifactAnalysis> matches = artifacts.stream().filter(predicate).toList();
        long versionCount = matches.stream().map(artifact -> artifact.coordinates().version()).filter(version -> version != null && !version.isBlank()).distinct().count();
        if (matches.size() > 1 && versionCount > 1) {
            findings.add(patternFinding("PATTERN_VERSION_SKEW", label, matches, severity,
                    "Align these libraries to a single version so transport, serialization, or utility behavior stays consistent across the graph."));
        }
    }

    private DuplicateClassFinding patternFinding(String type, String symbol, List<ArtifactAnalysis> artifacts, String severity, String recommendation) {
        return new DuplicateClassFinding(
                type,
                symbol,
                null,
                artifacts.stream().map(this::artifactLabel).distinct().toList(),
                severity,
                recommendation,
                "Mixed providers or version skew can create subtle runtime behavior changes even when the build succeeds."
        );
    }

    private String sourcePath(ArtifactAnalysis artifact) {
        Object value = artifact.rawMetadata().get("sourcePath");
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private String artifactLabel(ArtifactAnalysis artifact) {
        String groupId = artifact.coordinates().groupId();
        String artifactId = artifact.coordinates().artifactId();
        String version = artifact.coordinates().version();
        if (groupId != null && artifactId != null && version != null) {
            return groupId + ":" + artifactId + ":" + version + " [" + artifact.fileName() + "]";
        }
        return artifact.fileName();
    }

    private String packageName(String className) {
        String normalized = className.endsWith(".class")
                ? className.substring(0, className.length() - ".class".length())
                : className;
        int index = normalized.lastIndexOf('.');
        if (index <= 0) {
            return null;
        }
        return normalized.substring(0, index);
    }
}
