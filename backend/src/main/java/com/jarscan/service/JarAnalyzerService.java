package com.jarscan.service;

import com.jarscan.config.JarScanProperties;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.DependencyInfo;
import com.jarscan.dto.JavaVersionInfo;
import com.jarscan.dto.ManifestInfo;
import com.jarscan.dto.MavenCoordinates;
import com.jarscan.model.ModuleType;
import com.jarscan.model.Severity;
import com.jarscan.util.HashUtils;
import com.jarscan.util.JavaVersionMapper;
import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

@Service
public class JarAnalyzerService {

    private final JarScanProperties properties;

    public JarAnalyzerService(JarScanProperties properties) {
        this.properties = properties;
    }

    public ArtifactAnalysis analyze(Path path, Path workspaceDir, List<String> warnings) throws IOException {
        return analyzeInternal(
                path,
                path.getFileName().toString(),
                workspaceDir,
                warnings,
                0,
                null,
                new AtomicLong(),
                new HashSet<>()
        );
    }

    private ArtifactAnalysis analyzeInternal(
            Path path,
            String displayName,
            Path workspaceDir,
            List<String> warnings,
            int depth,
            String parentPath,
            AtomicLong extractedBytes,
            Set<String> ancestry
    ) throws IOException {
        String sha256 = HashUtils.sha256(path);
        try (JarFile jarFile = new JarFile(path.toFile())) {
            int entryCount = 0;
            boolean fatJar = false;
            boolean moduleInfoPresent = false;
            List<String> sampleEntries = new ArrayList<>();
            List<ArtifactAnalysis> nestedArtifacts = new ArrayList<>();
            Integer minMajor = null;
            Integer maxMajor = null;
            MavenCoordinates coordinates = new MavenCoordinates(null, null, null);
            ancestry.add(sha256);
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entryCount++;
                if (sampleEntries.size() < 12 && !entry.isDirectory()) {
                    sampleEntries.add(entry.getName());
                }
                if (!coordinates.isKnown() && entry.getName().startsWith("META-INF/maven/")) {
                    coordinates = readCoordinates(jarFile, entry, coordinates);
                }
                if ("module-info.class".equals(entry.getName())) {
                    moduleInfoPresent = true;
                }
                if (!fatJar && (entry.getName().startsWith("BOOT-INF/lib/")
                        || entry.getName().startsWith("WEB-INF/lib/")
                        || entry.getName().startsWith("lib/"))) {
                    fatJar = true;
                }
                if (shouldAnalyzeNested(entry, depth)) {
                    ArtifactAnalysis nestedArtifact = extractAndAnalyzeNestedJar(
                            jarFile,
                            entry,
                            workspaceDir,
                            warnings,
                            depth + 1,
                            displayName,
                            extractedBytes,
                            ancestry
                    );
                    if (nestedArtifact != null) {
                        nestedArtifacts.add(nestedArtifact);
                    }
                }
                if (entry.getName().endsWith(".class")) {
                    Integer majorVersion = readClassMajorVersion(jarFile, entry);
                    if (majorVersion != null) {
                        minMajor = minMajor == null ? majorVersion : Math.min(minMajor, majorVersion);
                        maxMajor = maxMajor == null ? majorVersion : Math.max(maxMajor, majorVersion);
                    }
                }
            }

            ManifestInfo manifestInfo = readManifestInfo(jarFile.getManifest());
            ModuleType moduleType = determineModuleType(moduleInfoPresent, manifestInfo.automaticModuleName());
            boolean multiRelease = "true".equalsIgnoreCase(manifestInfo.multiRelease());
            return new ArtifactAnalysis(
                    UUID.randomUUID().toString(),
                    displayName,
                    java.nio.file.Files.size(path),
                    sha256,
                    entryCount,
                    fatJar,
                    parentPath,
                    depth,
                    coordinates,
                    new JavaVersionInfo(minMajor, maxMajor, JavaVersionMapper.describe(maxMajor), multiRelease),
                    manifestInfo,
                    moduleType,
                    Severity.UNKNOWN,
                    0,
                    toDependencyInfo(nestedArtifacts),
                    List.of(),
                    nestedArtifacts,
                    Map.of(
                            "archiveType", archiveType(path),
                            "sampleEntries", sampleEntries,
                            "moduleInfoPresent", moduleInfoPresent,
                            "coordinatesKnown", coordinates.isKnown()
                    )
            );
        } finally {
            ancestry.remove(sha256);
        }
    }

    private String archiveType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".war")) {
            return "WAR";
        }
        if (fileName.endsWith(".ear")) {
            return "EAR";
        }
        return "JAR";
    }

    private Integer readClassMajorVersion(JarFile jarFile, ZipEntry entry) throws IOException {
        try (InputStream inputStream = jarFile.getInputStream(entry);
             DataInputStream dataInputStream = new DataInputStream(inputStream)) {
            if (dataInputStream.readInt() != 0xCAFEBABE) {
                return null;
            }
            dataInputStream.readUnsignedShort();
            return dataInputStream.readUnsignedShort();
        }
    }

    private ManifestInfo readManifestInfo(Manifest manifest) {
        if (manifest == null) {
            return new ManifestInfo(null, null, null, null, null, null, null, null, Map.of());
        }

        Attributes attributes = manifest.getMainAttributes();
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : attributes.entrySet()) {
            values.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }

        return new ManifestInfo(
                attributes.getValue("Main-Class"),
                attributes.getValue("Implementation-Title"),
                attributes.getValue("Implementation-Version"),
                attributes.getValue("Implementation-Vendor"),
                attributes.getValue("Created-By"),
                attributes.getValue("Build-Jdk"),
                attributes.getValue("Automatic-Module-Name"),
                attributes.getValue("Multi-Release"),
                values
        );
    }

    private ModuleType determineModuleType(boolean moduleInfoPresent, String automaticModuleName) {
        if (moduleInfoPresent) {
            return ModuleType.MODULAR_JAR;
        }
        if (automaticModuleName != null && !automaticModuleName.isBlank()) {
            return ModuleType.AUTOMATIC_MODULE;
        }
        return ModuleType.CLASSPATH_JAR;
    }

    private boolean shouldAnalyzeNested(ZipEntry entry, int depth) {
        if (depth >= properties.maxNestedJarDepth()) {
            return false;
        }
        if (entry.isDirectory()) {
            return false;
        }
        String name = entry.getName();
        boolean inKnownContainer = name.startsWith("BOOT-INF/lib/")
                || name.startsWith("WEB-INF/lib/")
                || name.startsWith("lib/");
        return inKnownContainer && (name.endsWith(".jar") || name.endsWith(".war"));
    }

    private ArtifactAnalysis extractAndAnalyzeNestedJar(
            JarFile jarFile,
            ZipEntry entry,
            Path workspaceDir,
            List<String> warnings,
            int depth,
            String parentPath,
            AtomicLong extractedBytes,
            Set<String> ancestry
    ) {
        try {
            long projected = extractedBytes.get() + Math.max(0L, entry.getSize());
            if (projected > properties.maxExtractedArchiveSizeBytes()) {
                warnings.add("Skipped nested archive " + entry.getName() + " because the extracted size limit was reached");
                return null;
            }

            Path nestedDir = java.nio.file.Files.createDirectories(workspaceDir.resolve("nested"));
            String suffix = entry.getName().endsWith(".war") ? ".war" : ".jar";
            Path extracted = java.nio.file.Files.createTempFile(nestedDir, "nested-", suffix);
            byte[] bytes;
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                bytes = readBytesCapped(inputStream, properties.maxExtractedArchiveSizeBytes() - extractedBytes.get());
            }
            extractedBytes.addAndGet(bytes.length);
            java.nio.file.Files.write(extracted, bytes);
            String nestedHash = HashUtils.sha256(extracted);
            if (ancestry.contains(nestedHash)) {
                warnings.add("Skipped recursive nested archive " + entry.getName());
                return null;
            }
            return analyzeInternal(extracted, entry.getName(), workspaceDir, warnings, depth, parentPath, extractedBytes, ancestry);
        } catch (IllegalStateException | IOException ex) {
            warnings.add("Unable to analyze nested archive " + entry.getName() + ": " + ex.getMessage());
            return null;
        }
    }

    private byte[] readBytesCapped(InputStream inputStream, long remainingBudget) throws IOException {
        if (remainingBudget <= 0) {
            throw new IllegalStateException("Extracted size limit reached");
        }
        byte[] buffer = new byte[8192];
        int read;
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        long total = 0;
        while ((read = inputStream.read(buffer)) >= 0) {
            total += read;
            if (total > remainingBudget) {
                throw new IllegalStateException("Nested archive exceeds the configured extraction budget");
            }
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private List<DependencyInfo> toDependencyInfo(List<ArtifactAnalysis> nestedArtifacts) {
        return nestedArtifacts.stream()
                .map(artifact -> new DependencyInfo(
                        artifact.fileName(),
                        artifact.coordinates(),
                        "embedded",
                        true,
                        artifact.javaVersion().requiredJava(),
                        artifact.vulnerabilityCount()
                ))
                .toList();
    }

    private MavenCoordinates readCoordinates(JarFile jarFile, ZipEntry entry, MavenCoordinates current) throws IOException {
        String name = entry.getName();
        if (name.endsWith("pom.properties")) {
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                Properties properties = new Properties();
                properties.load(inputStream);
                return new MavenCoordinates(
                        properties.getProperty("groupId"),
                        properties.getProperty("artifactId"),
                        properties.getProperty("version")
                );
            }
        }
        if (name.endsWith("pom.xml")) {
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                return parsePomXml(inputStream, current);
            }
        }
        return current;
    }

    private MavenCoordinates parsePomXml(InputStream inputStream, MavenCoordinates fallback) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            var builder = factory.newDocumentBuilder();
            var document = builder.parse(inputStream);
            var project = document.getDocumentElement();
            String groupId = textContent(project, "groupId");
            String artifactId = textContent(project, "artifactId");
            String version = textContent(project, "version");
            return new MavenCoordinates(
                    groupId != null ? groupId : fallback.groupId(),
                    artifactId != null ? artifactId : fallback.artifactId(),
                    version != null ? version : fallback.version()
            );
        } catch (Exception ex) {
            return fallback;
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
}
