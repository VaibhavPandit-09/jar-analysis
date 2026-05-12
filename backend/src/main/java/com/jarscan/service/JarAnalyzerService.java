package com.jarscan.service;

import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.DependencyInfo;
import com.jarscan.dto.JavaVersionInfo;
import com.jarscan.dto.ManifestInfo;
import com.jarscan.dto.MavenCoordinates;
import com.jarscan.model.ModuleType;
import com.jarscan.model.Severity;
import com.jarscan.util.HashUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarFile;

@Service
public class JarAnalyzerService {

    public ArtifactAnalysis analyze(Path path) throws IOException {
        try (JarFile jarFile = new JarFile(path.toFile())) {
            return new ArtifactAnalysis(
                    UUID.randomUUID().toString(),
                    path.getFileName().toString(),
                    java.nio.file.Files.size(path),
                    HashUtils.sha256(path),
                    jarFile.size(),
                    false,
                    null,
                    0,
                    new MavenCoordinates(null, null, null),
                    new JavaVersionInfo(null, null, "Unknown"),
                    new ManifestInfo(null, null, null, null, null, null, null, null, Map.of()),
                    ModuleType.CLASSPATH_JAR,
                    Severity.UNKNOWN,
                    0,
                    List.<DependencyInfo>of(),
                    List.of(),
                    List.of(),
                    Map.of("analysis", "placeholder")
            );
        }
    }
}
