package com.jarscan.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jarscan")
public record JarScanProperties(
        @NotBlank String dataDir,
        @Min(1) int maxNestedJarDepth,
        @Min(1) long maxExtractedArchiveSizeBytes,
        @Min(1) int mavenTimeoutSeconds,
        @NotBlank String mavenDependencyScope
) {
}
