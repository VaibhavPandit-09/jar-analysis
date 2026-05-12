package com.jarscan.service;

import com.jarscan.dto.JavaVersionInfo;
import com.jarscan.dto.ProjectStructureSummary;
import com.jarscan.util.JavaVersionMapper;
import org.springframework.stereotype.Service;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class ProjectStructureDetector {

    public ProjectStructureDetection detect(Path projectRoot, String archiveName, List<String> warnings) throws IOException {
        List<Path> pomFiles;
        List<Path> compiledClassDirectories = new ArrayList<>();
        List<Path> packagedArtifacts = new ArrayList<>();
        List<Path> dependencyLibraryDirectories = new ArrayList<>();
        List<String> springMetadataFiles = new ArrayList<>();
        List<String> serviceLoaderFiles = new ArrayList<>();
        List<String> resourceFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            pomFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("pom.xml"))
                    .sorted(Comparator.comparingInt(path -> projectRoot.relativize(path).getNameCount()))
                    .toList();
        }

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.forEach(path -> {
                String relative = normalize(projectRoot, path);
                if (relative == null) {
                    return;
                }
                String lower = relative.toLowerCase(Locale.ROOT);
                if (Files.isDirectory(path)) {
                    if (lower.endsWith("target/classes")
                            || lower.endsWith("web-inf/classes")
                            || lower.endsWith("boot-inf/classes")) {
                        compiledClassDirectories.add(path);
                    }
                    if (lower.endsWith("web-inf/lib") || lower.endsWith("boot-inf/lib") || lower.endsWith("/lib") || lower.equals("lib")) {
                        dependencyLibraryDirectories.add(path);
                    }
                    return;
                }
                if (lower.endsWith(".jar") || lower.endsWith(".war") || lower.endsWith(".ear")) {
                    if (lower.contains("/target/") || lower.startsWith("target/") || lower.contains("/web-inf/lib/") || lower.contains("/boot-inf/lib/") || lower.startsWith("lib/")) {
                        packagedArtifacts.add(path);
                    }
                }
                if (lower.endsWith("application.properties") || lower.endsWith("application.yml") || lower.endsWith("application.yaml")) {
                    resourceFiles.add(relative);
                }
                if (lower.endsWith("spring.factories") || lower.endsWith("autoconfiguration.imports")) {
                    springMetadataFiles.add(relative);
                }
                if (lower.startsWith("meta-inf/services/") || lower.contains("/meta-inf/services/")) {
                    serviceLoaderFiles.add(relative);
                }
            });
        }

        RootPomSelection selection = selectRootPom(projectRoot, pomFiles, archiveName, warnings);
        JavaVersionInfo compiledJavaVersion = detectCompiledJavaVersion(compiledClassDirectories);
        List<String> moduleDirectories = pomFiles.stream()
                .map(Path::getParent)
                .filter(parent -> parent != null && !parent.equals(projectRoot))
                .map(path -> normalize(projectRoot, path))
                .distinct()
                .toList();

        ProjectStructureSummary summary = new ProjectStructureSummary(
                archiveName,
                selection.rootPom().map(path -> normalize(projectRoot, path)).orElse(null),
                selection.reason(),
                pomFiles.size(),
                packagedArtifacts.size(),
                compiledClassDirectories.size(),
                dependencyLibraryDirectories.size(),
                pomFiles.stream().map(path -> normalize(projectRoot, path)).toList(),
                moduleDirectories,
                compiledClassDirectories.stream().map(path -> normalize(projectRoot, path)).toList(),
                packagedArtifacts.stream().map(path -> normalize(projectRoot, path)).distinct().toList(),
                dependencyLibraryDirectories.stream().map(path -> normalize(projectRoot, path)).distinct().toList(),
                springMetadataFiles.stream().distinct().toList(),
                serviceLoaderFiles.stream().distinct().toList(),
                resourceFiles.stream().distinct().toList(),
                compiledJavaVersion
        );
        return new ProjectStructureDetection(
                projectRoot,
                selection.rootPom().orElse(null),
                packagedArtifacts.stream().distinct().toList(),
                compiledClassDirectories.stream().distinct().toList(),
                summary
        );
    }

    private RootPomSelection selectRootPom(Path projectRoot, List<Path> pomFiles, String archiveName, List<String> warnings) {
        if (pomFiles.isEmpty()) {
            warnings.add("No pom.xml was detected in the project ZIP; Maven resolution was skipped.");
            return new RootPomSelection(Optional.empty(), "No pom.xml files found");
        }
        String archiveBase = archiveName.replaceFirst("(?i)\\.zip$", "");
        List<ScoredPom> scored = pomFiles.stream()
                .map(path -> scorePom(projectRoot, path, archiveBase))
                .sorted(Comparator.comparingInt(ScoredPom::score).reversed()
                        .thenComparing(scoredPom -> normalize(projectRoot, scoredPom.path())))
                .toList();
        ScoredPom winner = scored.getFirst();
        if (scored.size() > 1 && scored.get(1).score() == winner.score()) {
            warnings.add("Multiple pom.xml files scored equally for root detection; using " + normalize(projectRoot, winner.path()));
        }
        return new RootPomSelection(Optional.of(winner.path()), winner.reason());
    }

    private ScoredPom scorePom(Path projectRoot, Path pomPath, String archiveBase) {
        PomMetadata metadata = parsePomMetadata(pomPath);
        String relative = normalize(projectRoot, pomPath);
        int depth = projectRoot.relativize(pomPath).getNameCount();
        int score = 100 - (depth * 10);
        List<String> reasons = new ArrayList<>();
        if ("pom.xml".equals(relative)) {
            score += 300;
            reasons.add("pom.xml is at ZIP root");
        }
        if ("pom".equalsIgnoreCase(metadata.packaging()) && !metadata.modules().isEmpty()) {
            score += 200;
            reasons.add("packaging pom with declared modules");
        }
        if (metadata.artifactId() != null && archiveBase.toLowerCase(Locale.ROOT).contains(metadata.artifactId().toLowerCase(Locale.ROOT))) {
            score += 120;
            reasons.add("artifactId matches project archive name");
        }
        Path targetDir = pomPath.getParent() == null ? null : pomPath.getParent().resolve("target");
        if (targetDir != null && Files.exists(targetDir)) {
            score += 140;
            reasons.add("target output exists nearby");
        }
        if (metadata.modules().isEmpty() && depth == 1) {
            score += 30;
            reasons.add("shallow project POM");
        }
        return new ScoredPom(pomPath, score, reasons.isEmpty() ? "Best-effort shallowest pom.xml match" : String.join("; ", reasons));
    }

    private PomMetadata parsePomMetadata(Path pomPath) {
        try (InputStream inputStream = Files.newInputStream(pomPath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            var builder = factory.newDocumentBuilder();
            var document = builder.parse(inputStream);
            var project = document.getDocumentElement();
            String artifactId = textContent(project, "artifactId");
            String packaging = textContent(project, "packaging");
            List<String> modules = textContents(project, "module");
            return new PomMetadata(artifactId, packaging, modules);
        } catch (Exception ex) {
            return new PomMetadata(null, null, List.of());
        }
    }

    private JavaVersionInfo detectCompiledJavaVersion(List<Path> directories) throws IOException {
        Integer minMajor = null;
        Integer maxMajor = null;
        for (Path directory : directories) {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.filter(Files::isRegularFile).filter(file -> file.getFileName().toString().endsWith(".class")).toList()) {
                    Integer major = readClassMajorVersion(path);
                    if (major != null) {
                        minMajor = minMajor == null ? major : Math.min(minMajor, major);
                        maxMajor = maxMajor == null ? major : Math.max(maxMajor, major);
                    }
                }
            }
        }
        return new JavaVersionInfo(minMajor, maxMajor, JavaVersionMapper.describe(maxMajor), false);
    }

    private Integer readClassMajorVersion(Path classFile) throws IOException {
        try (InputStream inputStream = Files.newInputStream(classFile);
             DataInputStream dataInputStream = new DataInputStream(inputStream)) {
            if (dataInputStream.readInt() != 0xCAFEBABE) {
                return null;
            }
            dataInputStream.readUnsignedShort();
            return dataInputStream.readUnsignedShort();
        }
    }

    private String normalize(Path root, Path path) {
        try {
            return root.relativize(path).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String textContent(org.w3c.dom.Element element, String tagName) {
        var nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private List<String> textContents(org.w3c.dom.Element element, String tagName) {
        var nodes = element.getElementsByTagName(tagName);
        List<String> values = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            String text = nodes.item(index).getTextContent();
            if (text != null && !text.isBlank()) {
                values.add(text.trim());
            }
        }
        return values;
    }

    public record ProjectStructureDetection(
            Path projectRoot,
            Path rootPom,
            List<Path> packagedArtifacts,
            List<Path> compiledClassDirectories,
            ProjectStructureSummary summary
    ) {
    }

    private record PomMetadata(String artifactId, String packaging, List<String> modules) {
    }

    private record ScoredPom(Path path, int score, String reason) {
    }

    private record RootPomSelection(Optional<Path> rootPom, String reason) {
    }
}
