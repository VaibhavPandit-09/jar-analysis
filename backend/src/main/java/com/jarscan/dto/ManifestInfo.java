package com.jarscan.dto;

import java.util.Map;

public record ManifestInfo(
        String mainClass,
        String implementationTitle,
        String implementationVersion,
        String implementationVendor,
        String createdBy,
        String buildJdk,
        String automaticModuleName,
        String multiRelease,
        Map<String, String> attributes
) {
}
