package com.jarscan.service;

import com.jarscan.config.JarScanProperties;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.DependencyInfo;
import com.jarscan.dto.JavaVersionInfo;
import com.jarscan.dto.ManifestInfo;
import com.jarscan.dto.MavenCoordinates;
import com.jarscan.dto.PackagingInspection;
import com.jarscan.model.ModuleType;
import com.jarscan.model.Severity;
import com.jarscan.util.HashUtils;
import com.jarscan.util.JavaVersionMapper;
import com.jarscan.util.PackagingInspectionFactory;
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
        String archiveType = archiveType(path);
        try (JarFile jarFile = new JarFile(path.toFile())) {
            int entryCount = 0;
            boolean fatJar = false;
            boolean moduleInfoPresent = false;
            boolean bootInfClassesPresent = false;
            boolean bootInfLibPresent = false;
            boolean webInfClassesPresent = false;
            boolean webInfLibPresent = false;
            boolean libDirectoryPresent = false;
            boolean webXmlPresent = false;
            boolean applicationXmlPresent = false;
            boolean classpathIndexPresent = false;
            boolean layersIndexPresent = false;
            List<String> sampleEntries = new ArrayList<>();
            List<ArtifactAnalysis> nestedArtifacts = new ArrayList<>();
            List<String> modulePaths = new ArrayList<>();
            List<String> springMetadataFiles = new ArrayList<>();
            List<String> serviceLoaderFiles = new ArrayList<>();
            List<String> configFiles = new ArrayList<>();
            Integer minMajor = null;
            Integer maxMajor = null;
            int applicationClassCount = 0;
            int webInfLibCount = 0;
            int warModuleCount = 0;
            int jarModuleCount = 0;
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
                if (entry.getName().startsWith("BOOT-INF/classes/")) {
                    bootInfClassesPresent = true;
                }
                if (entry.getName().startsWith("BOOT-INF/lib/")) {
                    bootInfLibPresent = true;
                    if (!entry.isDirectory()) {
                        webInfLibCount++;
                    }
                }
                if (entry.getName().startsWith("WEB-INF/classes/")) {
                    webInfClassesPresent = true;
                }
                if (entry.getName().startsWith("WEB-INF/lib/")) {
                    webInfLibPresent = true;
                    if (!entry.isDirectory()) {
                        webInfLibCount++;
                    }
                }
                if (entry.getName().startsWith("lib/")) {
                    libDirectoryPresent = true;
                }
                if (!fatJar && (bootInfLibPresent || webInfLibPresent || libDirectoryPresent)) {
                    fatJar = true;
                }
                if ("WEB-INF/web.xml".equals(entry.getName())) {
                    webXmlPresent = true;
                }
                if ("META-INF/application.xml".equals(entry.getName())) {
                    applicationXmlPresent = true;
                }
                if ("BOOT-INF/classpath.idx".equals(entry.getName()) || "classpath.idx".equals(entry.getName())) {
                    classpathIndexPresent = true;
                }
                if ("BOOT-INF/layers.idx".equals(entry.getName()) || "layers.idx".equals(entry.getName())) {
                    layersIndexPresent = true;
                }
                if (isSpringMetadata(entry.getName())) {
                    springMetadataFiles.add(entry.getName());
                }
                if (isServiceLoaderMetadata(entry.getName())) {
                    serviceLoaderFiles.add(entry.getName());
                }
                if (isConfigFile(entry.getName())) {
                    configFiles.add(entry.getName());
                }
                if (isEarModule(archiveType, entry)) {
                    modulePaths.add(entry.getName());
                    if (entry.getName().toLowerCase().endsWith(".war")) {
                        warModuleCount++;
                    } else if (entry.getName().toLowerCase().endsWith(".jar")) {
                        jarModuleCount++;
                    }
                }
                if (shouldAnalyzeNested(entry, depth, archiveType)) {
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
                    if (entry.getName().startsWith("BOOT-INF/classes/")
                            || entry.getName().startsWith("WEB-INF/classes/")
                            || ("JAR".equals(archiveType)
                            && !entry.getName().startsWith("BOOT-INF/lib/")
                            && !entry.getName().startsWith("WEB-INF/lib/")
                            && !entry.getName().startsWith("META-INF/"))) {
                        applicationClassCount++;
                    }
                }
            }

            ManifestInfo manifestInfo = readManifestInfo(jarFile.getManifest());
            ModuleType moduleType = determineModuleType(moduleInfoPresent, manifestInfo.automaticModuleName());
            boolean multiRelease = "true".equalsIgnoreCase(manifestInfo.multiRelease());
            JavaVersionInfo javaVersionInfo = new JavaVersionInfo(minMajor, maxMajor, JavaVersionMapper.describe(maxMajor), multiRelease);
            PackagingInspection packagingInspection = buildPackagingInspection(
                    archiveType,
                    fatJar,
                    bootInfClassesPresent,
                    bootInfLibPresent,
                    webInfClassesPresent,
                    webInfLibPresent,
                    libDirectoryPresent,
                    webXmlPresent,
                    applicationXmlPresent,
                    classpathIndexPresent,
                    layersIndexPresent,
                    applicationClassCount,
                    webInfLibCount,
                    warModuleCount,
                    jarModuleCount,
                    nestedArtifacts,
                    modulePaths,
                    springMetadataFiles,
                    serviceLoaderFiles,
                    configFiles,
                    javaVersionInfo,
                    manifestInfo
            );
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
                    javaVersionInfo,
                    manifestInfo,
                    moduleType,
                    Severity.UNKNOWN,
                    0,
                    toDependencyInfo(nestedArtifacts),
                    List.of(),
                    nestedArtifacts,
                    Map.of(
                            "archiveType", archiveType,
                            "sampleEntries", sampleEntries,
                            "moduleInfoPresent", moduleInfoPresent,
                            "coordinatesKnown", coordinates.isKnown(),
                            "sourcePath", path.toAbsolutePath().toString()
                    ),
                    packagingInspection
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

    private boolean shouldAnalyzeNested(ZipEntry entry, int depth, String archiveType) {
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
        boolean earModule = "EAR".equals(archiveType) && !name.contains("/")
                && (name.endsWith(".jar") || name.endsWith(".war"));
        return (inKnownContainer || earModule) && (name.endsWith(".jar") || name.endsWith(".war") || name.endsWith(".ear"));
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

    private PackagingInspection buildPackagingInspection(
            String archiveType,
            boolean fatJar,
            boolean bootInfClassesPresent,
            boolean bootInfLibPresent,
            boolean webInfClassesPresent,
            boolean webInfLibPresent,
            boolean libDirectoryPresent,
            boolean webXmlPresent,
            boolean applicationXmlPresent,
            boolean classpathIndexPresent,
            boolean layersIndexPresent,
            int applicationClassCount,
            int webInfLibCount,
            int warModuleCount,
            int jarModuleCount,
            List<ArtifactAnalysis> nestedArtifacts,
            List<String> modulePaths,
            List<String> springMetadataFiles,
            List<String> serviceLoaderFiles,
            List<String> configFiles,
            JavaVersionInfo javaVersionInfo,
            ManifestInfo manifestInfo
    ) {
        String packagingType = "PLAIN_ARCHIVE";
        String applicationClassesLocation = null;
        String dependencyLibrariesLocation = null;

        if ("WAR".equals(archiveType)) {
            packagingType = "WAR";
            applicationClassesLocation = webInfClassesPresent ? "WEB-INF/classes" : null;
            dependencyLibrariesLocation = webInfLibPresent ? "WEB-INF/lib" : null;
        } else if ("EAR".equals(archiveType)) {
            packagingType = "EAR";
            dependencyLibrariesLocation = libDirectoryPresent ? "lib" : null;
        } else if (bootInfLibPresent || manifestInfo.attributes().containsKey("Spring-Boot-Version") || manifestInfo.attributes().containsKey("Start-Class")) {
            packagingType = "SPRING_BOOT_EXECUTABLE_JAR";
            applicationClassesLocation = bootInfClassesPresent ? "BOOT-INF/classes" : null;
            dependencyLibrariesLocation = bootInfLibPresent ? "BOOT-INF/lib" : null;
        } else if (fatJar || libDirectoryPresent || webInfLibPresent) {
            packagingType = "SHADED_OR_UBER_JAR";
            applicationClassesLocation = bootInfClassesPresent ? "BOOT-INF/classes" : (webInfClassesPresent ? "WEB-INF/classes" : "archive root");
            dependencyLibrariesLocation = bootInfLibPresent ? "BOOT-INF/lib" : (webInfLibPresent ? "WEB-INF/lib" : (libDirectoryPresent ? "lib" : null));
        }

        return PackagingInspectionFactory.create(
                packagingType,
                applicationClassesLocation,
                dependencyLibrariesLocation,
                applicationClassCount,
                javaVersionInfo.requiredJava(),
                manifestInfo.attributes().get("Spring-Boot-Version"),
                manifestInfo.attributes().get("Start-Class"),
                manifestInfo.mainClass(),
                layersIndexPresent,
                classpathIndexPresent,
                webXmlPresent,
                applicationXmlPresent,
                webInfLibCount,
                warModuleCount,
                jarModuleCount,
                modulePaths,
                springMetadataFiles,
                serviceLoaderFiles,
                configFiles,
                "Session 8 duplicate class analysis placeholder",
                nestedArtifacts
        );
    }

    private boolean isSpringMetadata(String entryName) {
        String lower = entryName.toLowerCase();
        return lower.endsWith("spring.factories") || lower.endsWith("autoconfiguration.imports");
    }

    private boolean isServiceLoaderMetadata(String entryName) {
        String normalized = entryName.replace('\\', '/').toLowerCase();
        return normalized.startsWith("meta-inf/services/") || normalized.contains("/meta-inf/services/");
    }

    private boolean isConfigFile(String entryName) {
        String lower = entryName.toLowerCase();
        return lower.endsWith("application.properties")
                || lower.endsWith("application.yml")
                || lower.endsWith("application.yaml");
    }

    private boolean isEarModule(String archiveType, ZipEntry entry) {
        String lower = entry.getName().toLowerCase();
        return "EAR".equals(archiveType)
                && !entry.isDirectory()
                && !entry.getName().contains("/")
                && (lower.endsWith(".war") || lower.endsWith(".jar"));
    }
}
