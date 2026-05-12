package com.jarscan.dto;

public record NestedLibrarySummary(
        String fileName,
        long sizeBytes,
        String javaVersion,
        int vulnerabilityCount,
        MavenCoordinates coordinates
) {
}
