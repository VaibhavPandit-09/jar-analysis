package com.jarscan.dto;

public record MavenCoordinates(
        String groupId,
        String artifactId,
        String version
) {
    public boolean isKnown() {
        return groupId != null && artifactId != null && version != null;
    }
}
