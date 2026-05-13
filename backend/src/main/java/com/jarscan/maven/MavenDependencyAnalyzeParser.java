package com.jarscan.maven;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MavenDependencyAnalyzeParser {

    public MavenDependencyAnalyzeResult parse(String output) {
        if (output == null || output.isBlank()) {
            return new MavenDependencyAnalyzeResult(List.of(), List.of(), List.of(), output == null ? "" : output);
        }

        Section section = Section.NONE;
        List<MavenDependencyAnalyzeEntry> usedDeclared = new ArrayList<>();
        List<MavenDependencyAnalyzeEntry> usedUndeclared = new ArrayList<>();
        List<MavenDependencyAnalyzeEntry> unusedDeclared = new ArrayList<>();

        for (String rawLine : output.split("\\R")) {
            String line = stripPrefix(rawLine).trim();
            if (line.isBlank()) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("used undeclared dependencies")) {
                section = Section.USED_UNDECLARED;
                continue;
            }
            if (lower.contains("unused declared dependencies")) {
                section = Section.UNUSED_DECLARED;
                continue;
            }
            if (lower.contains("used declared dependencies")) {
                section = Section.USED_DECLARED;
                continue;
            }
            if (lower.startsWith("none")) {
                continue;
            }

            MavenDependencyAnalyzeEntry entry = parseCoordinate(line);
            if (entry == null) {
                continue;
            }
            switch (section) {
                case USED_DECLARED -> usedDeclared.add(entry);
                case USED_UNDECLARED -> usedUndeclared.add(entry);
                case UNUSED_DECLARED -> unusedDeclared.add(entry);
                default -> {
                }
            }
        }

        return new MavenDependencyAnalyzeResult(List.copyOf(usedDeclared), List.copyOf(usedUndeclared), List.copyOf(unusedDeclared), output);
    }

    private String stripPrefix(String line) {
        return line.replaceFirst("^\\[(INFO|WARNING|ERROR)]\\s*", "");
    }

    private MavenDependencyAnalyzeEntry parseCoordinate(String line) {
        String[] segments = line.split(":");
        if (segments.length < 5) {
            return null;
        }
        String groupId = segments[0].trim();
        String artifactId = segments[1].trim();
        String type = segments[2].trim();
        String classifier = null;
        String version;
        String scope;
        if (segments.length == 5) {
            version = segments[3].trim();
            scope = segments[4].trim();
        } else {
            classifier = segments[3].trim();
            version = segments[4].trim();
            scope = segments[5].trim();
        }
        return new MavenDependencyAnalyzeEntry(groupId, artifactId, type, classifier, version, scope);
    }

    private enum Section {
        NONE,
        USED_DECLARED,
        USED_UNDECLARED,
        UNUSED_DECLARED
    }
}
