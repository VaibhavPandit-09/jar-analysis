package com.jarscan.service;

import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.LicenseFinding;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarFile;

@Service
public class LicenseAnalysisService {

    public List<LicenseFinding> analyze(List<ArtifactAnalysis> flattenedArtifacts, List<String> warnings) {
        Map<String, LicenseFinding> findings = new LinkedHashMap<>();
        for (ArtifactAnalysis artifact : flattenedArtifacts) {
            String sourcePath = sourcePath(artifact);
            if (sourcePath == null) {
                continue;
            }
            try {
                LicenseFinding finding = inspectArtifact(artifact, Path.of(sourcePath));
                findings.putIfAbsent(findingKey(artifact), finding);
            } catch (IOException ex) {
                warnings.add("Unable to extract license metadata from " + artifact.fileName() + ": " + ex.getMessage());
            }
        }
        return List.copyOf(findings.values());
    }

    LicenseFinding inspectArtifact(ArtifactAnalysis artifact, Path path) throws IOException {
        LinkedHashSet<LicenseEvidence> evidence = new LinkedHashSet<>();
        try (JarFile jarFile = new JarFile(path.toFile())) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith("META-INF/maven/") && name.endsWith("pom.xml")) {
                    try (InputStream inputStream = jarFile.getInputStream(entry)) {
                        evidence.addAll(parsePomLicenses(inputStream));
                    }
                } else if (looksLikeLicenseFile(name) && evidence.isEmpty()) {
                    try (InputStream inputStream = jarFile.getInputStream(entry)) {
                        String guessed = guessLicenseName(inputStream.readNBytes(4096));
                        if (guessed != null) {
                            evidence.add(new LicenseEvidence(guessed, null, "LICENSE_FILE", "LOW"));
                        }
                    }
                }
            }
        }

        String bundleLicense = artifact.manifest().attributes().get("Bundle-License");
        if (bundleLicense != null && !bundleLicense.isBlank()) {
            evidence.add(new LicenseEvidence(bundleLicense.trim(), null, "MANIFEST", "MEDIUM"));
        }

        if (evidence.isEmpty()) {
            return new LicenseFinding(
                    artifact.coordinates().groupId(),
                    artifact.coordinates().artifactId(),
                    artifact.coordinates().version(),
                    "Unknown",
                    null,
                    "NONE",
                    "LOW",
                    "unknown",
                    List.of("No embedded license metadata was found for this archive."),
                    false,
                    null,
                    null
            );
        }

        List<String> licenseNames = evidence.stream().map(LicenseEvidence::name).distinct().toList();
        String category = licenseNames.size() > 1 ? "multiple" : classify(licenseNames.getFirst());
        List<String> findingWarnings = new ArrayList<>();
        if ("multiple".equals(category)) {
            findingWarnings.add("Multiple licenses were detected for this dependency.");
        }
        if ("strong copyleft".equals(category)) {
            findingWarnings.add("Strong copyleft license detected. Review redistribution obligations carefully.");
        }
        if ("unknown".equals(category)) {
            findingWarnings.add("License could not be classified confidently from the available evidence.");
        }

        LicenseEvidence primary = evidence.getFirst();
        return new LicenseFinding(
                artifact.coordinates().groupId(),
                artifact.coordinates().artifactId(),
                artifact.coordinates().version(),
                String.join(", ", licenseNames),
                primary.url(),
                primary.source(),
                primary.confidence(),
                category,
                List.copyOf(findingWarnings),
                false,
                null,
                null
        );
    }

    String classify(String licenseName) {
        if (licenseName == null || licenseName.isBlank()) {
            return "unknown";
        }
        String normalized = licenseName.toLowerCase(Locale.ROOT);
        if (normalized.contains("apache")) {
            return "permissive";
        }
        if (normalized.contains("mit")) {
            return "permissive";
        }
        if (normalized.contains("bsd")) {
            return "permissive";
        }
        if (normalized.contains("epl") || normalized.contains("eclipse public license")) {
            return "weak copyleft";
        }
        if (normalized.contains("lgpl")) {
            return "weak copyleft";
        }
        if (normalized.contains("agpl")) {
            return "strong copyleft";
        }
        if (normalized.contains("gpl") || normalized.contains("general public license")) {
            return "strong copyleft";
        }
        if (normalized.contains("commercial") || normalized.contains("proprietary")) {
            return "commercial";
        }
        return "unknown";
    }

    private List<LicenseEvidence> parsePomLicenses(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            var builder = factory.newDocumentBuilder();
            var document = builder.parse(inputStream);
            var licenseNodes = document.getElementsByTagName("license");
            List<LicenseEvidence> licenses = new ArrayList<>();
            for (int index = 0; index < licenseNodes.getLength(); index++) {
                if (!(licenseNodes.item(index) instanceof Element element)) {
                    continue;
                }
                String name = textContent(element, "name");
                String url = textContent(element, "url");
                if (name != null) {
                    licenses.add(new LicenseEvidence(name, url, "EMBEDDED_POM", "HIGH"));
                }
            }
            return licenses;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String textContent(Element element, String tagName) {
        var nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private boolean looksLikeLicenseFile(String entryName) {
        String normalized = entryName.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.endsWith("license")
                || normalized.endsWith("license.txt")
                || normalized.endsWith("license.md")
                || normalized.endsWith("copying")
                || normalized.endsWith("notice");
    }

    private String guessLicenseName(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (text.contains("apache license")) {
            return "Apache-2.0";
        }
        if (text.contains("mit license")) {
            return "MIT";
        }
        if (text.contains("bsd license") || text.contains("redistribution and use in source and binary forms")) {
            return "BSD";
        }
        if (text.contains("eclipse public license")) {
            return "EPL";
        }
        if (text.contains("lesser general public license")) {
            return "LGPL";
        }
        if (text.contains("affero general public license")) {
            return "AGPL";
        }
        if (text.contains("general public license")) {
            return "GPL";
        }
        return null;
    }

    private String sourcePath(ArtifactAnalysis artifact) {
        Object value = artifact.rawMetadata().get("sourcePath");
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private String findingKey(ArtifactAnalysis artifact) {
        if (artifact.coordinates().groupId() != null && artifact.coordinates().artifactId() != null && artifact.coordinates().version() != null) {
            return artifact.coordinates().groupId() + ":" + artifact.coordinates().artifactId() + ":" + artifact.coordinates().version();
        }
        return artifact.fileName() + ":" + artifact.sha256();
    }

    private record LicenseEvidence(String name, String url, String source, String confidence) {
    }
}
