package com.jarscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarscan.dto.AnalysisResult;
import com.jarscan.dto.AnalysisSummary;
import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.DependencyInfo;
import com.jarscan.dto.DependencyTree;
import com.jarscan.dto.DependencyTreeNode;
import com.jarscan.dto.JavaVersionInfo;
import com.jarscan.dto.LicenseFinding;
import com.jarscan.dto.ManifestInfo;
import com.jarscan.dto.MavenCoordinates;
import com.jarscan.model.InputType;
import com.jarscan.model.JobStatus;
import com.jarscan.model.ModuleType;
import com.jarscan.model.Severity;
import com.jarscan.util.AnalysisSummaryFactory;
import com.jarscan.util.HashUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SbomService {

    private final ObjectMapper objectMapper;

    public SbomService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisResult importCycloneDx(MultipartFile file) {
        String fileName = file.getOriginalFilename() == null ? "sbom.json" : file.getOriginalFilename();
        validate(fileName, file.getContentType());
        try {
            byte[] bytes = file.getBytes();
            JsonNode root = objectMapper.readTree(bytes);
            if (!"CycloneDX".equals(root.path("bomFormat").asText())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only CycloneDX JSON SBOM imports are supported right now");
            }
            List<Component> components = parseComponents(root.path("components"));
            DependencyTree dependencyTree = buildDependencyTree(root.path("metadata").path("component"), components, root.path("dependencies"));
            List<ArtifactAnalysis> artifacts = components.stream().map(this::toArtifact).toList();
            List<LicenseFinding> licenses = buildLicenses(components);
            List<String> warnings = new ArrayList<>();
            if (components.isEmpty()) {
                warnings.add("The imported SBOM did not contain any CycloneDX components.");
            }
            warnings.add("Imported SBOM quality depends on the source SBOM completeness. Vulnerability results are best-effort and may be absent for imported SBOM-only scans.");

            AnalysisSummary summary = AnalysisSummaryFactory.create(
                    InputType.SBOM,
                    artifacts,
                    List.of(),
                    List.of(),
                    List.of(),
                    licenses,
                    List.of(),
                    List.of(),
                    null,
                    null
            );
            Instant now = Instant.now();
            return new AnalysisResult(
                    "sbom-" + UUID.randomUUID(),
                    JobStatus.COMPLETED,
                    InputType.SBOM,
                    now,
                    now,
                    summary,
                    artifacts,
                    dependencyTree,
                    List.of(),
                    List.of(),
                    List.of(),
                    licenses,
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    warnings,
                    List.of(),
                    null
            );
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to parse CycloneDX JSON SBOM", ex);
        }
    }

    public byte[] exportCycloneDx(AnalysisResult result) {
        Map<String, Object> bom = new LinkedHashMap<>();
        bom.put("bomFormat", "CycloneDX");
        bom.put("specVersion", "1.5");
        bom.put("serialNumber", "urn:uuid:" + UUID.randomUUID());
        bom.put("version", 1);
        bom.put("metadata", Map.of(
                "timestamp", Instant.now().toString(),
                "tools", List.of(Map.of("vendor", "OpenAI", "name", "JARScan", "version", "v2"))
        ));

        List<ArtifactAnalysis> flattened = AnalysisSummaryFactory.flattenArtifacts(result.artifacts());
        Map<String, LicenseFinding> licensesByCoordinate = new HashMap<>();
        for (LicenseFinding license : result.licenses()) {
            licensesByCoordinate.put(coordinate(license.groupId(), license.artifactId(), license.version()), license);
        }

        List<Map<String, Object>> components = new ArrayList<>();
        for (ArtifactAnalysis artifact : flattened) {
            String ref = coordinate(artifact.coordinates().groupId(), artifact.coordinates().artifactId(), artifact.coordinates().version());
            Map<String, Object> component = new LinkedHashMap<>();
            component.put("bom-ref", ref);
            component.put("type", artifact.moduleType() == ModuleType.CLASSPATH_JAR ? "library" : "application");
            component.put("group", artifact.coordinates().groupId());
            component.put("name", artifact.coordinates().artifactId() == null ? artifact.fileName() : artifact.coordinates().artifactId());
            component.put("version", artifact.coordinates().version());
            component.put("purl", purl(artifact.coordinates().groupId(), artifact.coordinates().artifactId(), artifact.coordinates().version()));
            component.put("hashes", List.of(Map.of("alg", "SHA-256", "content", artifact.sha256())));
            LicenseFinding license = licensesByCoordinate.get(ref);
            if (license != null) {
                component.put("licenses", List.of(Map.of("license", Map.of(
                        "name", license.licenseName(),
                        "url", license.licenseUrl() == null ? "" : license.licenseUrl()
                ))));
            }
            components.add(component);
        }
        bom.put("components", components);

        if (result.dependencyTree() != null && !result.dependencyTree().roots().isEmpty()) {
            List<Map<String, Object>> dependencies = new ArrayList<>();
            for (DependencyTreeNode root : result.dependencyTree().roots()) {
                collectDependencies(root, dependencies);
            }
            bom.put("dependencies", dependencies);
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(bom);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to serialize CycloneDX JSON", ex);
        }
    }

    private void collectDependencies(DependencyTreeNode node, List<Map<String, Object>> dependencies) {
        List<String> dependsOn = node.children().stream().map(this::coordinate).toList();
        dependencies.add(Map.of(
                "ref", coordinate(node),
                "dependsOn", dependsOn
        ));
        node.children().forEach(child -> collectDependencies(child, dependencies));
    }

    private DependencyTree buildDependencyTree(JsonNode metadataComponent, List<Component> components, JsonNode dependenciesNode) {
        Map<String, Component> componentsByRef = new LinkedHashMap<>();
        for (Component component : components) {
            componentsByRef.put(component.ref(), component);
        }
        Map<String, List<String>> relationships = new LinkedHashMap<>();
        Set<String> childRefs = new HashSet<>();
        if (dependenciesNode.isArray()) {
            for (JsonNode dependency : dependenciesNode) {
                String ref = dependency.path("ref").asText(null);
                if (ref == null || ref.isBlank()) {
                    continue;
                }
                List<String> dependsOn = new ArrayList<>();
                JsonNode dependsOnNode = dependency.path("dependsOn");
                if (dependsOnNode.isArray()) {
                    for (JsonNode child : dependsOnNode) {
                        String childRef = child.asText(null);
                        if (childRef != null && !childRef.isBlank()) {
                            dependsOn.add(childRef);
                            childRefs.add(childRef);
                        }
                    }
                }
                relationships.put(ref, dependsOn);
            }
        }

        List<DependencyTreeNode> roots = new ArrayList<>();
        if (metadataComponent != null && !metadataComponent.isMissingNode() && metadataComponent.path("name").isTextual()) {
            String rootRef = metadataComponent.path("bom-ref").asText("root-component");
            Component rootComponent = new Component(
                    rootRef,
                    metadataComponent.path("group").asText(null),
                    metadataComponent.path("name").asText(null),
                    metadataComponent.path("version").asText(null),
                    null,
                    List.of(),
                    null
            );
            List<DependencyTreeNode> children = relationships.getOrDefault(rootRef, components.stream().map(Component::ref).toList()).stream()
                    .map(ref -> buildTreeNode(ref, componentsByRef, relationships, rootRef, 1, List.of(rootComponent.artifactId() == null ? "root" : rootComponent.artifactId())))
                    .toList();
            roots.add(new DependencyTreeNode(
                    rootRef,
                    rootComponent.groupId(),
                    rootComponent.artifactId(),
                    "pom",
                    null,
                    rootComponent.version(),
                    "runtime",
                    0,
                    null,
                    children,
                    true,
                    false,
                    false,
                    null,
                    false,
                    null,
                    List.of(rootComponent.artifactId() == null ? "root" : rootComponent.artifactId())
            ));
            return new DependencyTree("cyclonedx-json", roots);
        }

        for (Component component : components) {
            if (!childRefs.contains(component.ref())) {
                roots.add(buildTreeNode(component.ref(), componentsByRef, relationships, null, 0, List.of()));
            }
        }
        return new DependencyTree("cyclonedx-json", roots);
    }

    private DependencyTreeNode buildTreeNode(String ref, Map<String, Component> componentsByRef, Map<String, List<String>> relationships, String parentId, int depth, List<String> ancestors) {
        Component component = componentsByRef.get(ref);
        if (component == null) {
            return new DependencyTreeNode(ref, null, ref, "library", null, null, "runtime", depth, parentId, List.of(), depth <= 1, depth > 1, false, null, false, null, ancestors);
        }
        List<String> path = new ArrayList<>(ancestors);
        path.add(component.artifactId() == null ? ref : component.artifactId());
        List<DependencyTreeNode> children = relationships.getOrDefault(ref, List.of()).stream()
                .map(child -> buildTreeNode(child, componentsByRef, relationships, ref, depth + 1, path))
                .toList();
        return new DependencyTreeNode(
                ref,
                component.groupId(),
                component.artifactId(),
                component.type() == null ? "library" : component.type(),
                null,
                component.version(),
                "runtime",
                depth,
                parentId,
                children,
                depth <= 1,
                depth > 1,
                false,
                null,
                false,
                null,
                path
        );
    }

    private ArtifactAnalysis toArtifact(Component component) {
        return new ArtifactAnalysis(
                component.ref(),
                component.artifactId() == null ? component.ref() : component.artifactId() + (component.version() == null ? "" : "-" + component.version()),
                0,
                component.hash() == null ? HashUtils.sha256(component.ref().getBytes(StandardCharsets.UTF_8)) : component.hash(),
                0,
                false,
                null,
                0,
                new MavenCoordinates(component.groupId(), component.artifactId(), component.version()),
                new JavaVersionInfo(null, null, "Unknown", false),
                new ManifestInfo(null, null, component.version(), null, null, null, null, null, Map.of()),
                ModuleType.CLASSPATH_JAR,
                Severity.UNKNOWN,
                0,
                List.of(),
                List.of(),
                List.of(),
                Map.of(
                        "sbomRef", component.ref(),
                        "purl", component.purl() == null ? purl(component.groupId(), component.artifactId(), component.version()) : component.purl(),
                        "source", "CycloneDX JSON import"
                ),
                null
        );
    }

    private List<LicenseFinding> buildLicenses(List<Component> components) {
        List<LicenseFinding> findings = new ArrayList<>();
        for (Component component : components) {
            if (component.licenses().isEmpty()) {
                findings.add(new LicenseFinding(
                        component.groupId(),
                        component.artifactId(),
                        component.version(),
                        "Unknown",
                        null,
                        "sbom",
                        "medium",
                        "unknown",
                        List.of("The imported SBOM did not provide a resolvable license declaration for this component."),
                        false,
                        null,
                        null
                ));
                continue;
            }
            for (LicenseInfo license : component.licenses()) {
                findings.add(new LicenseFinding(
                        component.groupId(),
                        component.artifactId(),
                        component.version(),
                        license.name() == null ? "Unknown" : license.name(),
                        license.url(),
                        "sbom",
                        "medium",
                        classifyLicense(license.name()),
                        "unknown".equals(classifyLicense(license.name()))
                                ? List.of("Review this imported SBOM license declaration before relying on it for policy decisions.")
                                : List.of(),
                        false,
                        null,
                        null
                ));
            }
        }
        return findings;
    }

    private List<Component> parseComponents(JsonNode componentsNode) {
        List<Component> components = new ArrayList<>();
        if (!componentsNode.isArray()) {
            return components;
        }
        for (JsonNode node : componentsNode) {
            String ref = node.path("bom-ref").asText(null);
            String group = node.path("group").asText(null);
            String name = node.path("name").asText(null);
            String version = node.path("version").asText(null);
            String purl = node.path("purl").asText(null);
            String hash = null;
            JsonNode hashes = node.path("hashes");
            if (hashes.isArray()) {
                for (JsonNode item : hashes) {
                    if ("SHA-256".equalsIgnoreCase(item.path("alg").asText())) {
                        hash = item.path("content").asText(null);
                        break;
                    }
                }
            }
            List<LicenseInfo> licenses = new ArrayList<>();
            JsonNode licensesNode = node.path("licenses");
            if (licensesNode.isArray()) {
                for (JsonNode licenseNode : licensesNode) {
                    JsonNode detail = licenseNode.path("license");
                    licenses.add(new LicenseInfo(detail.path("name").asText(null), detail.path("url").asText(null)));
                }
            }
            components.add(new Component(ref == null || ref.isBlank() ? coordinate(group, name, version) : ref, group, name, version, purl, licenses, hash));
        }
        return components;
    }

    private String classifyLicense(String licenseName) {
        if (licenseName == null || licenseName.isBlank()) {
            return "unknown";
        }
        String normalized = licenseName.toLowerCase();
        if (normalized.contains("apache") || normalized.contains("mit") || normalized.contains("bsd")) {
            return "permissive";
        }
        if (normalized.contains("epl") || normalized.contains("lgpl")) {
            return "weak copyleft";
        }
        if (normalized.contains("agpl") || normalized.contains("gpl")) {
            return "strong copyleft";
        }
        return "unknown";
    }

    private String coordinate(String groupId, String artifactId, String version) {
        return (groupId == null ? "unknown" : groupId)
                + ":" + (artifactId == null ? "unknown" : artifactId)
                + ":" + (version == null ? "unknown" : version);
    }

    private String coordinate(DependencyTreeNode node) {
        return coordinate(node.groupId(), node.artifactId(), node.version());
    }

    private String purl(String groupId, String artifactId, String version) {
        if (groupId == null || artifactId == null || version == null) {
            return null;
        }
        return "pkg:maven/" + groupId + "/" + artifactId + "@" + version;
    }

    private void validate(String fileName, String contentType) {
        String lower = fileName.toLowerCase();
        if (!(lower.endsWith(".json") || lower.endsWith(".cdx.json") || lower.endsWith(".bom.json"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SBOM import expects a CycloneDX JSON file (.json, .cdx.json, or .bom.json)");
        }
        if (contentType != null && !MediaType.APPLICATION_JSON_VALUE.equals(contentType) && !contentType.contains("json") && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SBOM import expects JSON content");
        }
    }

    private record Component(String ref, String groupId, String artifactId, String version, String purl, List<LicenseInfo> licenses, String hash) {
        String type() {
            return "library";
        }
    }

    private record LicenseInfo(String name, String url) {
    }
}
