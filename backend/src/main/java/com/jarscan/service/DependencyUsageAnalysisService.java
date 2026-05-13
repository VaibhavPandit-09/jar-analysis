package com.jarscan.service;

import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.DependencyTree;
import com.jarscan.dto.DependencyTreeNode;
import com.jarscan.dto.DependencyUsageFinding;
import com.jarscan.dto.ProjectStructureSummary;
import com.jarscan.maven.MavenDependencyAnalyzeEntry;
import com.jarscan.maven.MavenDependencyAnalyzeResult;
import com.jarscan.model.InputType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

@Service
public class DependencyUsageAnalysisService {

    static final String RUNTIME_WARNING = "Java dependencies may be used through reflection, configuration, ServiceLoader, Spring auto-configuration, servlet containers, logging frameworks, JDBC drivers, or runtime plugin loading. Removal suggestions should be reviewed and tested.";

    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+([a-zA-Z0-9_.*$]+)\\s*;");
    private static final Map<String, String> AWS_SERVICE_PREFIXES = Map.ofEntries(
            Map.entry("software.amazon.awssdk.services.s3.", "s3"),
            Map.entry("software.amazon.awssdk.services.dynamodb.", "dynamodb"),
            Map.entry("software.amazon.awssdk.services.sqs.", "sqs"),
            Map.entry("software.amazon.awssdk.services.sns.", "sns"),
            Map.entry("software.amazon.awssdk.services.lambda.", "lambda"),
            Map.entry("software.amazon.awssdk.services.kinesis.", "kinesis"),
            Map.entry("software.amazon.awssdk.services.ec2.", "ec2"),
            Map.entry("software.amazon.awssdk.services.cloudwatch.", "cloudwatch"),
            Map.entry("com.amazonaws.services.s3.", "s3"),
            Map.entry("com.amazonaws.services.dynamodbv2.", "dynamodb"),
            Map.entry("com.amazonaws.services.sqs.", "sqs"),
            Map.entry("com.amazonaws.services.sns.", "sns"),
            Map.entry("com.amazonaws.services.lambda.", "lambda"),
            Map.entry("com.amazonaws.services.kinesis.", "kinesis"),
            Map.entry("com.amazonaws.services.ec2.", "ec2"),
            Map.entry("com.amazonaws.services.cloudwatch.", "cloudwatch")
    );

    private final BytecodeReferenceExtractorService bytecodeReferenceExtractorService;
    private final DependencyPathService dependencyPathService;

    public DependencyUsageAnalysisService(
            BytecodeReferenceExtractorService bytecodeReferenceExtractorService,
            DependencyPathService dependencyPathService
    ) {
        this.bytecodeReferenceExtractorService = bytecodeReferenceExtractorService;
        this.dependencyPathService = dependencyPathService;
    }

    public DependencyUsageAnalysisResult analyze(
            InputType inputType,
            List<ArtifactAnalysis> rootArtifacts,
            List<Path> resolvedArtifactPaths,
            DependencyTree dependencyTree,
            MavenDependencyAnalyzeResult dependencyAnalyzeResult,
            Path projectRoot,
            ProjectStructureSummary projectStructure,
            List<Path> compiledClassDirectories,
            List<Path> applicationArchives,
            List<String> warnings
    ) {
        List<ArtifactAnalysis> flattenedArtifacts = flattenArtifacts(rootArtifacts);
        Map<String, CandidateDependency> candidates = buildCandidates(inputType, flattenedArtifacts, resolvedArtifactPaths, dependencyTree);
        if (candidates.isEmpty() && (dependencyAnalyzeResult == null || dependencyAnalyzeResult.usedUndeclaredDependencies().isEmpty())) {
            return new DependencyUsageAnalysisResult(List.of(), Set.of());
        }

        ApplicationEvidence applicationEvidence = collectApplicationEvidence(projectRoot, projectStructure, compiledClassDirectories, applicationArchives, warnings);
        Map<String, Set<String>> providersByClass = new LinkedHashMap<>();
        for (CandidateDependency candidate : candidates.values()) {
            indexProvidedClasses(candidate, providersByClass, warnings);
        }

        Map<String, Set<String>> matchedDependenciesByBytecode = matchReferences(applicationEvidence.referencedClasses(), providersByClass);
        Map<String, Set<String>> matchedDependenciesBySource = matchReferences(applicationEvidence.sourceImportedClasses(), providersByClass);
        Map<String, Set<String>> matchedDependenciesByResources = matchReferences(applicationEvidence.resourceReferencedClasses(), providersByClass);

        Set<String> usedDeclaredKeys = toDependencyKeys(dependencyAnalyzeResult == null ? List.of() : dependencyAnalyzeResult.usedDeclaredDependencies());
        Set<String> usedUndeclaredKeys = toDependencyKeys(dependencyAnalyzeResult == null ? List.of() : dependencyAnalyzeResult.usedUndeclaredDependencies());
        Set<String> unusedDeclaredKeys = toDependencyKeys(dependencyAnalyzeResult == null ? List.of() : dependencyAnalyzeResult.unusedDeclaredDependencies());

        List<DependencyUsageFinding> findings = new ArrayList<>();
        for (CandidateDependency candidate : candidates.values()) {
            findings.add(buildFinding(
                    candidate,
                    applicationEvidence,
                    matchedDependenciesByBytecode,
                    matchedDependenciesBySource,
                    matchedDependenciesByResources,
                    usedDeclaredKeys,
                    unusedDeclaredKeys,
                    dependencyAnalyzeResult != null,
                    inputType
            ));
        }

        if (dependencyAnalyzeResult != null) {
            for (MavenDependencyAnalyzeEntry entry : dependencyAnalyzeResult.usedUndeclaredDependencies()) {
                String dependencyKey = entry.dependencyKey();
                if (candidates.values().stream().anyMatch(candidate -> dependencyKey.equals(candidate.dependencyKey()))) {
                    continue;
                }
                findings.add(new DependencyUsageFinding(
                        entry.groupId(),
                        entry.artifactId(),
                        entry.version(),
                        "USED_UNDECLARED",
                        "MEDIUM",
                        List.of(
                                "Maven dependency:analyze reported this dependency as used but undeclared.",
                                "The artifact was not part of the resolved artifact set that JARScan analyzed directly."
                        ),
                        List.of(RUNTIME_WARNING),
                        "Declare this dependency explicitly in Maven and verify that the build no longer relies on transitive leakage.",
                        findPaths(dependencyTree, dependencyKey),
                        null,
                        null
                ));
            }
        }

        findings.sort(Comparator
                .comparing((DependencyUsageFinding finding) -> statusRank(finding.status()))
                .thenComparing(finding -> (finding.groupId() == null ? "zzzz" : finding.groupId()) + ":" + (finding.artifactId() == null ? "zzzz" : finding.artifactId())));
        return new DependencyUsageAnalysisResult(List.copyOf(findings), Set.copyOf(applicationEvidence.awsServiceModules()));
    }

    private List<ArtifactAnalysis> flattenArtifacts(List<ArtifactAnalysis> artifacts) {
        List<ArtifactAnalysis> flattened = new ArrayList<>();
        for (ArtifactAnalysis artifact : artifacts) {
            flattened.add(artifact);
            flattened.addAll(flattenArtifacts(artifact.nestedArtifacts()));
        }
        return flattened;
    }

    private Map<String, CandidateDependency> buildCandidates(
            InputType inputType,
            List<ArtifactAnalysis> flattenedArtifacts,
            List<Path> resolvedArtifactPaths,
            DependencyTree dependencyTree
    ) {
        Set<String> resolvedPaths = resolvedArtifactPaths == null ? Set.of() : resolvedArtifactPaths.stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, TreeMetadata> metadataByKey = collectTreeMetadata(dependencyTree);
        Map<String, CandidateDependency> candidates = new LinkedHashMap<>();
        for (ArtifactAnalysis artifact : flattenedArtifacts) {
            if (artifact.coordinates() == null || !artifact.coordinates().isKnown()) {
                continue;
            }
            String sourcePath = sourcePath(artifact);
            boolean fromResolvedArtifacts = sourcePath != null && resolvedPaths.contains(sourcePath);
            boolean packagedDependency = artifact.parentPath() != null || artifact.nestedDepth() > 0;
            boolean include = switch (inputType) {
                case POM -> true;
                case PROJECT_ZIP -> fromResolvedArtifacts || packagedDependency;
                case ARCHIVE_UPLOAD -> packagedDependency;
            };
            if (!include) {
                continue;
            }
            String key = dependencyKey(artifact.coordinates().groupId(), artifact.coordinates().artifactId(), artifact.coordinates().version());
            TreeMetadata treeMetadata = metadataByKey.getOrDefault(key, metadataByKey.get(gaKey(artifact.coordinates().groupId(), artifact.coordinates().artifactId())));
            candidates.put(key, new CandidateDependency(
                    artifact,
                    treeMetadata == null ? null : treeMetadata.paths(),
                    treeMetadata != null && treeMetadata.direct(),
                    packagedDependency,
                    treeMetadata == null ? null : treeMetadata.transitiveDependencyCount()
            ));
        }
        return candidates;
    }

    private Map<String, TreeMetadata> collectTreeMetadata(DependencyTree dependencyTree) {
        if (dependencyTree == null || dependencyTree.roots() == null) {
            return Map.of();
        }
        Map<String, TreeMetadata> metadata = new LinkedHashMap<>();
        for (DependencyTreeNode root : dependencyTree.roots()) {
            collectTreeMetadata(root, metadata);
        }
        return metadata;
    }

    private void collectTreeMetadata(DependencyTreeNode node, Map<String, TreeMetadata> metadata) {
        if (node.depth() > 0 && node.groupId() != null && node.artifactId() != null) {
            String exactKey = dependencyKey(node.groupId(), node.artifactId(), node.version());
            String gaKey = gaKey(node.groupId(), node.artifactId());
            TreeMetadata value = new TreeMetadata(
                    List.of(node.pathFromRoot()),
                    node.direct(),
                    countDescendants(node)
            );
            mergeTreeMetadata(metadata, exactKey, value);
            mergeTreeMetadata(metadata, gaKey, value);
        }
        node.children().forEach(child -> collectTreeMetadata(child, metadata));
    }

    private void mergeTreeMetadata(Map<String, TreeMetadata> metadata, String key, TreeMetadata update) {
        TreeMetadata existing = metadata.get(key);
        if (existing == null) {
            metadata.put(key, update);
            return;
        }
        List<List<String>> mergedPaths = new ArrayList<>(existing.paths());
        mergedPaths.addAll(update.paths());
        metadata.put(key, new TreeMetadata(List.copyOf(mergedPaths), existing.direct() || update.direct(), Math.max(existing.transitiveDependencyCount(), update.transitiveDependencyCount())));
    }

    private int countDescendants(DependencyTreeNode node) {
        int count = node.children().size();
        for (DependencyTreeNode child : node.children()) {
            count += countDescendants(child);
        }
        return count;
    }

    private ApplicationEvidence collectApplicationEvidence(
            Path projectRoot,
            ProjectStructureSummary projectStructure,
            List<Path> compiledClassDirectories,
            List<Path> applicationArchives,
            List<String> warnings
    ) {
        Set<String> referencedClasses = new LinkedHashSet<>();
        Set<String> sourceImportedClasses = new LinkedHashSet<>();
        Set<String> resourceReferencedClasses = new LinkedHashSet<>();
        Set<String> awsServices = new LinkedHashSet<>();

        boolean compiledClassEvidenceAvailable = false;
        if (compiledClassDirectories != null) {
            for (Path directory : compiledClassDirectories) {
                if (directory == null || !Files.exists(directory)) {
                    continue;
                }
                compiledClassEvidenceAvailable = true;
                try {
                    referencedClasses.addAll(bytecodeReferenceExtractorService.extractReferencedClasses(directory));
                } catch (IOException ex) {
                    warnings.add("Failed to read compiled class references from " + directory.getFileName() + ": " + ex.getMessage());
                }
            }
        }

        if (applicationArchives != null) {
            for (Path archive : applicationArchives) {
                if (archive == null || !Files.exists(archive)) {
                    continue;
                }
                compiledClassEvidenceAvailable = true;
                try {
                    referencedClasses.addAll(bytecodeReferenceExtractorService.extractReferencedClassesFromArchive(archive, this::isApplicationClassEntry));
                    resourceReferencedClasses.addAll(readArchiveResourceHints(archive));
                } catch (IOException ex) {
                    warnings.add("Failed to inspect packaged application archive " + archive.getFileName() + ": " + ex.getMessage());
                }
            }
        }

        if (!compiledClassEvidenceAvailable && projectRoot != null && Files.exists(projectRoot)) {
            sourceImportedClasses.addAll(scanSourceImports(projectRoot, warnings));
        }

        if (projectRoot != null && projectStructure != null) {
            resourceReferencedClasses.addAll(scanProjectResources(projectRoot, projectStructure, warnings));
        }

        referencedClasses.forEach(reference -> collectAwsService(reference, awsServices));
        sourceImportedClasses.forEach(reference -> collectAwsService(reference, awsServices));
        resourceReferencedClasses.forEach(reference -> collectAwsService(reference, awsServices));

        return new ApplicationEvidence(
                Set.copyOf(referencedClasses),
                Set.copyOf(sourceImportedClasses),
                Set.copyOf(resourceReferencedClasses),
                compiledClassEvidenceAvailable,
                Set.copyOf(awsServices)
        );
    }

    private void collectAwsService(String reference, Set<String> awsServices) {
        for (Map.Entry<String, String> entry : AWS_SERVICE_PREFIXES.entrySet()) {
            if (reference.startsWith(entry.getKey())) {
                awsServices.add(entry.getValue());
            }
        }
    }

    private void indexProvidedClasses(CandidateDependency candidate, Map<String, Set<String>> providersByClass, List<String> warnings) {
        String sourcePath = sourcePath(candidate.artifact());
        if (sourcePath == null) {
            return;
        }
        try {
            Set<String> providedClasses = bytecodeReferenceExtractorService.extractProvidedClasses(Path.of(sourcePath));
            candidate.providedClasses().addAll(providedClasses);
            for (String providedClass : providedClasses) {
                providersByClass.computeIfAbsent(providedClass, key -> new LinkedHashSet<>()).add(candidate.uniqueKey());
                int lastDot = providedClass.lastIndexOf('.');
                if (lastDot > 0) {
                    candidate.providedPackages().add(providedClass.substring(0, lastDot));
                }
            }
        } catch (IOException ex) {
            warnings.add("Failed to inspect dependency classes for " + candidate.displayName() + ": " + ex.getMessage());
        }
    }

    private Map<String, Set<String>> matchReferences(Set<String> references, Map<String, Set<String>> providersByClass) {
        Map<String, Set<String>> matches = new HashMap<>();
        for (String reference : references) {
            Set<String> providers = providersByClass.get(reference);
            if (providers == null || providers.isEmpty()) {
                continue;
            }
            for (String provider : providers) {
                matches.computeIfAbsent(provider, key -> new LinkedHashSet<>()).add(reference);
            }
        }
        return matches;
    }

    private DependencyUsageFinding buildFinding(
            CandidateDependency candidate,
            ApplicationEvidence applicationEvidence,
            Map<String, Set<String>> matchedDependenciesByBytecode,
            Map<String, Set<String>> matchedDependenciesBySource,
            Map<String, Set<String>> matchedDependenciesByResources,
            Set<String> usedDeclaredKeys,
            Set<String> unusedDeclaredKeys,
            boolean hasMavenAnalyzeEvidence,
            InputType inputType
    ) {
        List<String> evidence = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        warnings.add(RUNTIME_WARNING);

        String dependencyKey = candidate.dependencyKey();
        boolean bytecodeMatch = matchedDependenciesByBytecode.containsKey(candidate.uniqueKey());
        boolean sourceImportMatch = matchedDependenciesBySource.containsKey(candidate.uniqueKey());
        boolean resourceMatch = matchedDependenciesByResources.containsKey(candidate.uniqueKey());
        boolean usedDeclared = usedDeclaredKeys.contains(dependencyKey);
        boolean unusedDeclared = unusedDeclaredKeys.contains(dependencyKey);
        boolean hasServiceLoaderMetadata = candidate.hasServiceLoaderMetadata();
        boolean hasSpringMetadata = candidate.hasSpringMetadata();
        List<String> runtimeHints = knownRuntimeHints(candidate);
        boolean likelyRuntimeArtifact = !runtimeHints.isEmpty();
        boolean hasStrongMetadataEvidence = hasServiceLoaderMetadata || hasSpringMetadata;
        boolean noDirectReferenceEvidence = !bytecodeMatch && !sourceImportMatch && !resourceMatch && !usedDeclared;

        if (bytecodeMatch) {
            evidence.add("Compiled bytecode references matched classes packaged by this dependency.");
        }
        if (sourceImportMatch) {
            evidence.add("Source imports matched classes packaged by this dependency when compiled class evidence was unavailable.");
        }
        if (resourceMatch) {
            evidence.add("Resource or configuration content referenced classes packaged by this dependency.");
        }
        if (usedDeclared) {
            evidence.add("Maven dependency:analyze reported this dependency as used and declared.");
        }
        if (unusedDeclared) {
            evidence.add("Maven dependency:analyze reported this dependency as declared but unused.");
        }
        if (hasServiceLoaderMetadata) {
            evidence.add("The dependency contains ServiceLoader provider metadata, which often indicates runtime wiring.");
        }
        if (hasSpringMetadata) {
            evidence.add("The dependency contains Spring auto-configuration metadata, which can activate through runtime configuration.");
        }
        runtimeHints.forEach(hint -> evidence.add("Runtime heuristic: " + hint));

        String status;
        String confidence;
        if (bytecodeMatch || usedDeclared) {
            status = "USED";
            confidence = "HIGH";
        } else if (sourceImportMatch || resourceMatch) {
            status = "USED";
            confidence = applicationEvidence.compiledClassEvidenceAvailable() ? "MEDIUM" : "LOW";
        } else if (unusedDeclared && noDirectReferenceEvidence && !hasStrongMetadataEvidence && !likelyRuntimeArtifact && applicationEvidence.compiledClassEvidenceAvailable()) {
            status = "DECLARED_BUT_UNUSED";
            confidence = "HIGH";
        } else if (noDirectReferenceEvidence && !hasStrongMetadataEvidence && !likelyRuntimeArtifact && applicationEvidence.compiledClassEvidenceAvailable()) {
            status = candidate.packagedDependency() ? "PACKAGED_BUT_APPARENTLY_UNUSED" : "APPARENTLY_UNUSED";
            confidence = hasMavenAnalyzeEvidence ? "HIGH" : "MEDIUM";
        } else if (noDirectReferenceEvidence && (hasStrongMetadataEvidence || likelyRuntimeArtifact || resourceMatch)) {
            status = "POSSIBLY_RUNTIME_USED";
            confidence = applicationEvidence.compiledClassEvidenceAvailable() ? "LOW" : "LOW";
        } else if (!applicationEvidence.compiledClassEvidenceAvailable() && applicationEvidence.sourceImportedClasses().isEmpty() && !hasMavenAnalyzeEvidence) {
            status = "UNKNOWN";
            confidence = "LOW";
        } else {
            status = "UNKNOWN";
            confidence = applicationEvidence.compiledClassEvidenceAvailable() ? "MEDIUM" : "LOW";
        }

        if ("DECLARED_BUT_UNUSED".equals(status) || "APPARENTLY_UNUSED".equals(status) || "PACKAGED_BUT_APPARENTLY_UNUSED".equals(status)) {
            evidence.add("No compiled class references mapped to this dependency.");
            if (!hasMavenAnalyzeEvidence) {
                evidence.add("No Maven dependency:analyze confirmation was available, so this remains evidence-based rather than absolute.");
            }
        }
        if ("POSSIBLY_RUNTIME_USED".equals(status)) {
            warnings.add("Runtime framework hints were found for this dependency, so removal confidence is intentionally low.");
        }
        if ("UNKNOWN".equals(status)) {
            warnings.add("JARScan did not have enough compile-time or Maven evidence to classify this dependency confidently.");
        }

        return new DependencyUsageFinding(
                candidate.artifact().coordinates().groupId(),
                candidate.artifact().coordinates().artifactId(),
                candidate.artifact().coordinates().version(),
                status,
                confidence,
                List.copyOf(evidence),
                List.copyOf(warnings),
                suggestedAction(status),
                candidate.paths() == null ? List.of() : candidate.paths(),
                candidate.artifact().sizeBytes(),
                candidate.artifact().vulnerabilityCount()
        );
    }

    private List<String> knownRuntimeHints(CandidateDependency candidate) {
        String groupId = candidate.artifact().coordinates().groupId();
        String artifactId = candidate.artifact().coordinates().artifactId();
        if (groupId == null || artifactId == null) {
            return List.of();
        }
        String key = (groupId + ":" + artifactId).toLowerCase(Locale.ROOT);
        List<String> hints = new ArrayList<>();
        if (key.contains("jdbc") || key.contains("postgresql") || key.contains("mysql") || key.contains("mariadb") || key.contains("ojdbc") || key.contains("h2")) {
            hints.add("JDBC drivers are frequently loaded from configuration or container wiring.");
        }
        if (key.contains("slf4j") || key.contains("logback") || key.contains("log4j")) {
            hints.add("Logging implementations and bridges are often activated indirectly at runtime.");
        }
        if (artifactId.contains("starter") || artifactId.contains("autoconfigure")) {
            hints.add("Spring Boot starters and auto-configure modules can be activated without direct bytecode references.");
        }
        if (key.contains("servlet") || key.contains("tomcat") || key.contains("jetty") || key.contains("undertow")) {
            hints.add("Servlet container integrations may be wired through packaging or runtime environment rather than direct references.");
        }
        if (artifactId.contains("processor") || artifactId.contains("lombok")) {
            hints.add("Annotation processor style dependencies may not show normal runtime bytecode references.");
        }
        if (artifactId.contains("plugin") || artifactId.contains("spi")) {
            hints.add("Plugin and SPI-style dependencies may load through discovery rather than direct calls.");
        }
        if (key.contains("jackson") || key.contains("hibernate") || key.contains("reflect")) {
            hints.add("Reflection-heavy frameworks can be used through metadata or serialization configuration.");
        }
        return List.copyOf(hints);
    }

    private String suggestedAction(String status) {
        return switch (status) {
            case "USED" -> "Keep this dependency unless you are intentionally replacing it with a narrower module or a newer version.";
            case "USED_UNDECLARED" -> "Declare this dependency explicitly in Maven and re-run the build so usage no longer depends on transitive leakage.";
            case "POSSIBLY_RUNTIME_USED" -> "Review runtime wiring, configuration, and framework activation before considering removal. Test startup and integration paths carefully.";
            case "DECLARED_BUT_UNUSED", "APPARENTLY_UNUSED", "PACKAGED_BUT_APPARENTLY_UNUSED" -> "Review this dependency for removal or exclusion, then run tests and startup verification before keeping the change.";
            default -> "Gather more evidence before changing this dependency, then validate the application after any removal or exclusion attempt.";
        };
    }

    private Set<String> scanSourceImports(Path projectRoot, List<String> warnings) {
        Set<String> imports = new LinkedHashSet<>();
        try (var paths = Files.walk(projectRoot)) {
            for (Path javaFile : paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).toList()) {
                try {
                    for (String line : Files.readAllLines(javaFile)) {
                        Matcher matcher = IMPORT_PATTERN.matcher(line);
                        if (matcher.find()) {
                            imports.add(matcher.group(1).replace(".*", ""));
                        }
                    }
                } catch (IOException ex) {
                    warnings.add("Failed to read source imports from " + javaFile.getFileName() + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            warnings.add("Failed to scan Java source imports: " + ex.getMessage());
        }
        return imports;
    }

    private Set<String> scanProjectResources(Path projectRoot, ProjectStructureSummary projectStructure, List<String> warnings) {
        Set<String> referencedClasses = new LinkedHashSet<>();
        List<String> candidates = new ArrayList<>();
        candidates.addAll(projectStructure.resourceFiles());
        candidates.addAll(projectStructure.serviceLoaderFiles());
        candidates.addAll(projectStructure.springMetadataFiles());
        for (String relative : candidates) {
            Path file = projectRoot.resolve(relative);
            if (!Files.exists(file) || Files.isDirectory(file)) {
                continue;
            }
            try {
                referencedClasses.addAll(extractClassLikeTokens(Files.readString(file)));
            } catch (IOException ex) {
                warnings.add("Failed to inspect resource file " + relative + ": " + ex.getMessage());
            }
        }
        return referencedClasses;
    }

    private Set<String> readArchiveResourceHints(Path archive) throws IOException {
        Set<String> referencedClasses = new LinkedHashSet<>();
        try (var jarFile = new java.util.jar.JarFile(archive.toFile())) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String lower = entry.getName().toLowerCase(Locale.ROOT);
                boolean interesting = lower.endsWith("application.properties")
                        || lower.endsWith("application.yml")
                        || lower.endsWith("application.yaml")
                        || lower.endsWith("spring.factories")
                        || lower.endsWith("autoconfiguration.imports")
                        || lower.endsWith(".xml")
                        || lower.startsWith("meta-inf/services/")
                        || lower.contains("/meta-inf/services/");
                if (!interesting) {
                    continue;
                }
                try (var inputStream = jarFile.getInputStream(entry)) {
                    referencedClasses.addAll(extractClassLikeTokens(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
        }
        return referencedClasses;
    }

    private Set<String> extractClassLikeTokens(String content) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("([a-zA-Z_][\\w$]*\\.)+[A-Z][\\w$]*").matcher(content);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private boolean isApplicationClassEntry(String entryName) {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("BOOT-INF/classes/") || normalized.startsWith("WEB-INF/classes/")) {
            return true;
        }
        return !normalized.startsWith("BOOT-INF/lib/")
                && !normalized.startsWith("WEB-INF/lib/")
                && !normalized.startsWith("META-INF/");
    }

    private Set<String> toDependencyKeys(Collection<MavenDependencyAnalyzeEntry> entries) {
        return entries.stream().map(MavenDependencyAnalyzeEntry::dependencyKey).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<List<String>> findPaths(DependencyTree dependencyTree, String dependencyKey) {
        return dependencyPathService.findPaths(dependencyTree, dependencyKey).stream()
                .map(path -> path.stream().map(node -> formatNode(node)).toList())
                .toList();
    }

    private String formatNode(DependencyTreeNode node) {
        return node.groupId() + ":" + node.artifactId() + ":" + (node.version() == null ? "unknown" : node.version());
    }

    private int statusRank(String status) {
        return switch (status) {
            case "USED_UNDECLARED" -> 0;
            case "DECLARED_BUT_UNUSED", "APPARENTLY_UNUSED", "PACKAGED_BUT_APPARENTLY_UNUSED" -> 1;
            case "POSSIBLY_RUNTIME_USED" -> 2;
            case "UNKNOWN" -> 3;
            default -> 4;
        };
    }

    private String sourcePath(ArtifactAnalysis artifact) {
        if (artifact.rawMetadata() == null) {
            return null;
        }
        Object sourcePath = artifact.rawMetadata().get("sourcePath");
        return sourcePath instanceof String ? Path.of((String) sourcePath).toAbsolutePath().normalize().toString() : null;
    }

    private String dependencyKey(String groupId, String artifactId, String version) {
        return groupId + ":" + artifactId + ":" + version;
    }

    private String gaKey(String groupId, String artifactId) {
        return groupId + ":" + artifactId;
    }

    private record TreeMetadata(List<List<String>> paths, boolean direct, int transitiveDependencyCount) {
    }

    private record ApplicationEvidence(
            Set<String> referencedClasses,
            Set<String> sourceImportedClasses,
            Set<String> resourceReferencedClasses,
            boolean compiledClassEvidenceAvailable,
            Set<String> awsServiceModules
    ) {
    }

    private static final class CandidateDependency {
        private final ArtifactAnalysis artifact;
        private final List<List<String>> paths;
        private final boolean direct;
        private final boolean packagedDependency;
        private final Integer transitiveDependencyCount;
        private final Set<String> providedClasses = new LinkedHashSet<>();
        private final Set<String> providedPackages = new LinkedHashSet<>();

        private CandidateDependency(
                ArtifactAnalysis artifact,
                List<List<String>> paths,
                boolean direct,
                boolean packagedDependency,
                Integer transitiveDependencyCount
        ) {
            this.artifact = artifact;
            this.paths = paths == null ? List.of() : List.copyOf(paths);
            this.direct = direct;
            this.packagedDependency = packagedDependency;
            this.transitiveDependencyCount = transitiveDependencyCount;
        }

        ArtifactAnalysis artifact() {
            return artifact;
        }

        List<List<String>> paths() {
            return paths;
        }

        boolean direct() {
            return direct;
        }

        boolean packagedDependency() {
            return packagedDependency;
        }

        Integer transitiveDependencyCount() {
            return transitiveDependencyCount;
        }

        Set<String> providedClasses() {
            return providedClasses;
        }

        Set<String> providedPackages() {
            return providedPackages;
        }

        boolean hasServiceLoaderMetadata() {
            return artifact.packagingInspection() != null && !artifact.packagingInspection().serviceLoaderFiles().isEmpty();
        }

        boolean hasSpringMetadata() {
            return artifact.packagingInspection() != null && !artifact.packagingInspection().springMetadataFiles().isEmpty();
        }

        String displayName() {
            return artifact.fileName();
        }

        String uniqueKey() {
            return artifact.coordinates().groupId() + ":" + artifact.coordinates().artifactId() + ":" + artifact.coordinates().version();
        }

        String dependencyKey() {
            return artifact.coordinates().groupId() + ":" + artifact.coordinates().artifactId();
        }
    }

    public record DependencyUsageAnalysisResult(
            List<DependencyUsageFinding> dependencyUsageFindings,
            Set<String> awsServiceModules
    ) {
    }
}
