package com.jarscan.service;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

@Service
public class JarAnalyzerService {

    public ArtifactAnalysis analyze(Path path) throws IOException {
        try (JarFile jarFile = new JarFile(path.toFile())) {
            int entryCount = 0;
            boolean fatJar = false;
            boolean moduleInfoPresent = false;
            List<String> sampleEntries = new ArrayList<>();
            Integer minMajor = null;
            Integer maxMajor = null;
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entryCount++;
                if (sampleEntries.size() < 12 && !entry.isDirectory()) {
                    sampleEntries.add(entry.getName());
                }
                if ("module-info.class".equals(entry.getName())) {
                    moduleInfoPresent = true;
                }
                if (!fatJar && (entry.getName().startsWith("BOOT-INF/lib/")
                        || entry.getName().startsWith("WEB-INF/lib/")
                        || entry.getName().startsWith("lib/"))) {
                    fatJar = true;
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
                    path.getFileName().toString(),
                    java.nio.file.Files.size(path),
                    HashUtils.sha256(path),
                    entryCount,
                    fatJar,
                    null,
                    0,
                    new MavenCoordinates(null, null, null),
                    new JavaVersionInfo(minMajor, maxMajor, JavaVersionMapper.describe(maxMajor), multiRelease),
                    manifestInfo,
                    moduleType,
                    Severity.UNKNOWN,
                    0,
                    List.<DependencyInfo>of(),
                    List.of(),
                    List.of(),
                    Map.of(
                            "archiveType", archiveType(path),
                            "sampleEntries", sampleEntries,
                            "moduleInfoPresent", moduleInfoPresent
                    )
            );
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
}
