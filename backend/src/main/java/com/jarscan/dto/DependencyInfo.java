package com.jarscan.dto;

public record DependencyInfo(
        String artifact,
        MavenCoordinates coordinates,
        String scope,
        boolean direct,
        String javaVersion,
        int vulnerabilityCount
) {
}
