package com.jarscan.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarscan.dto.DependencyTree;
import com.jarscan.dto.DependencyTreeNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MavenDependencyTreeParser {

    private static final Pattern INFO_PREFIX = Pattern.compile("^\\[INFO\\]\\s?");
    private static final Pattern TREE_BRANCH_PATTERN = Pattern.compile("^(?<prefix>(?:\\|  |   )*)(?<branch>\\+- |\\\\- )(?<body>.+)$");
    private static final Pattern NOTE_PATTERN = Pattern.compile("\\(([^)]+)\\)");

    private final ObjectMapper objectMapper;

    public MavenDependencyTreeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DependencyTree parseJson(String rawJson) throws IOException {
        JsonNode rootNode = objectMapper.readTree(extractJsonBody(rawJson));
        if (rootNode == null || rootNode.isNull()) {
            return new DependencyTree("JSON", List.of());
        }
        AtomicInteger sequence = new AtomicInteger();
        return new DependencyTree("JSON", List.of(toNode(rootNode, 0, null, List.of(), sequence)));
    }

    public DependencyTree parseText(String rawText) {
        List<MutableTreeNode> roots = new ArrayList<>();
        List<MutableTreeNode> stack = new ArrayList<>();

        for (String line : rawText.split("\\R")) {
            String normalized = stripInfoPrefix(line).trim();
            if (normalized.isBlank()) {
                continue;
            }
            ParsedTextLine parsed = parseTextLine(normalized);
            if (parsed == null) {
                continue;
            }

            MutableTreeNode node = new MutableTreeNode(
                    parsed.groupId(),
                    parsed.artifactId(),
                    parsed.type(),
                    parsed.classifier(),
                    parsed.version(),
                    parsed.scope(),
                    parsed.depth(),
                    parsed.omitted(),
                    parsed.omittedReason(),
                    parsed.conflict(),
                    parsed.rawLine()
            );

            while (stack.size() > parsed.depth()) {
                stack.removeLast();
            }

            if (parsed.depth() > 0 && stack.size() >= parsed.depth()) {
                MutableTreeNode parent = stack.get(parsed.depth() - 1);
                parent.children().add(node);
            } else {
                roots.add(node);
            }
            stack.add(node);
        }

        AtomicInteger sequence = new AtomicInteger();
        List<DependencyTreeNode> immutableRoots = roots.stream()
                .map(root -> toNode(root, null, List.of(), sequence))
                .toList();
        return new DependencyTree("TEXT", immutableRoots);
    }

    private DependencyTreeNode toNode(JsonNode source, int depth, String parentId, List<String> ancestorPath, AtomicInteger sequence) {
        String groupId = textValue(source, "groupId");
        String artifactId = textValue(source, "artifactId");
        String version = textValue(source, "version");
        String type = textValue(source, "type");
        String classifier = textValue(source, "classifier");
        String scope = textValue(source, "scope");
        String nodeId = "dep-node-" + sequence.incrementAndGet();
        List<String> pathFromRoot = appendPath(ancestorPath, coordinateLabel(groupId, artifactId, version));
        List<DependencyTreeNode> children = new ArrayList<>();
        JsonNode childArray = source.path("children");
        if (childArray.isArray()) {
            for (JsonNode child : childArray) {
                children.add(toNode(child, depth + 1, nodeId, pathFromRoot, sequence));
            }
        }
        return new DependencyTreeNode(
                nodeId,
                groupId,
                artifactId,
                type,
                classifier,
                version,
                scope,
                depth,
                parentId,
                List.copyOf(children),
                depth == 1,
                depth > 1,
                false,
                null,
                false,
                null,
                pathFromRoot
        );
    }

    private DependencyTreeNode toNode(MutableTreeNode source, String parentId, List<String> ancestorPath, AtomicInteger sequence) {
        String nodeId = "dep-node-" + sequence.incrementAndGet();
        List<String> pathFromRoot = appendPath(ancestorPath, coordinateLabel(source.groupId(), source.artifactId(), source.version()));
        List<DependencyTreeNode> children = source.children().stream()
                .map(child -> toNode(child, nodeId, pathFromRoot, sequence))
                .toList();
        return new DependencyTreeNode(
                nodeId,
                source.groupId(),
                source.artifactId(),
                source.type(),
                source.classifier(),
                source.version(),
                source.scope(),
                source.depth(),
                parentId,
                children,
                source.depth() == 1,
                source.depth() > 1,
                source.omitted(),
                source.omittedReason(),
                source.conflict(),
                source.rawLine(),
                pathFromRoot
        );
    }

    private ParsedTextLine parseTextLine(String line) {
        Matcher branchMatcher = TREE_BRANCH_PATTERN.matcher(line);
        String body = line;
        int depth = 0;
        if (branchMatcher.matches()) {
            depth = branchMatcher.group("prefix").length() / 3 + 1;
            body = branchMatcher.group("body").trim();
        }

        ParsedCoordinate coordinate = parseCoordinate(body, depth == 0);
        if (coordinate == null) {
            return null;
        }

        List<String> notes = extractNotes(coordinate.noteText());
        String omittedReason = notes.stream()
                .filter(note -> note.toLowerCase(Locale.ROOT).startsWith("omitted for"))
                .findFirst()
                .orElse(null);
        boolean conflict = notes.stream()
                .anyMatch(note -> note.toLowerCase(Locale.ROOT).contains("omitted for conflict"));

        return new ParsedTextLine(
                coordinate.groupId(),
                coordinate.artifactId(),
                coordinate.type(),
                coordinate.classifier(),
                coordinate.version(),
                coordinate.scope(),
                depth,
                omittedReason != null,
                omittedReason,
                conflict,
                line
        );
    }

    private ParsedCoordinate parseCoordinate(String body, boolean root) {
        int noteStart = body.indexOf(" (");
        String coordinatePart = noteStart >= 0 ? body.substring(0, noteStart).trim() : body.trim();
        String noteText = noteStart >= 0 ? body.substring(noteStart).trim() : "";
        String[] parts = coordinatePart.split(":");
        if (parts.length < 4) {
            return null;
        }
        if (parts.length == 4) {
            return new ParsedCoordinate(parts[0], parts[1], parts[2], null, parts[3], null, noteText);
        }
        if (parts.length == 5) {
            return new ParsedCoordinate(parts[0], parts[1], parts[2], null, parts[3], parts[4], noteText);
        }
        if (parts.length >= 6) {
            return new ParsedCoordinate(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], noteText);
        }
        if (root) {
            return new ParsedCoordinate(parts[0], parts[1], parts[2], null, parts[3], null, noteText);
        }
        return null;
    }

    private List<String> extractNotes(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> notes = new ArrayList<>();
        Matcher matcher = NOTE_PATTERN.matcher(text);
        while (matcher.find()) {
            notes.add(matcher.group(1).trim());
        }
        return notes;
    }

    private String extractJsonBody(String rawJson) {
        StringBuilder builder = new StringBuilder();
        for (String line : rawJson.split("\\R")) {
            builder.append(stripInfoPrefix(line)).append(System.lineSeparator());
        }
        String normalized = builder.toString();
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            return normalized.substring(start, end + 1);
        }
        return normalized;
    }

    private String stripInfoPrefix(String line) {
        return INFO_PREFIX.matcher(line).replaceFirst("");
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private List<String> appendPath(List<String> existing, String coordinate) {
        List<String> path = new ArrayList<>(existing);
        path.add(coordinate);
        return List.copyOf(path);
    }

    private String coordinateLabel(String groupId, String artifactId, String version) {
        return "%s:%s:%s".formatted(
                groupId == null ? "unknown" : groupId,
                artifactId == null ? "unknown" : artifactId,
                version == null ? "unknown" : version
        );
    }

    private record ParsedCoordinate(
            String groupId,
            String artifactId,
            String type,
            String classifier,
            String version,
            String scope,
            String noteText
    ) {
    }

    private record ParsedTextLine(
            String groupId,
            String artifactId,
            String type,
            String classifier,
            String version,
            String scope,
            int depth,
            boolean omitted,
            String omittedReason,
            boolean conflict,
            String rawLine
    ) {
    }

    private record MutableTreeNode(
            String groupId,
            String artifactId,
            String type,
            String classifier,
            String version,
            String scope,
            int depth,
            boolean omitted,
            String omittedReason,
            boolean conflict,
            String rawLine,
            List<MutableTreeNode> children
    ) {
        private MutableTreeNode(
                String groupId,
                String artifactId,
                String type,
                String classifier,
                String version,
                String scope,
                int depth,
                boolean omitted,
                String omittedReason,
                boolean conflict,
                String rawLine
        ) {
            this(groupId, artifactId, type, classifier, version, scope, depth, omitted, omittedReason, conflict, rawLine, new ArrayList<>());
        }
    }
}
