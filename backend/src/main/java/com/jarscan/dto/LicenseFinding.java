package com.jarscan.dto;

import java.util.List;

public record LicenseFinding(
        String groupId,
        String artifactId,
        String version,
        String licenseName,
        String licenseUrl,
        String source,
        String confidence,
        String category,
        List<String> warnings
) {
}
