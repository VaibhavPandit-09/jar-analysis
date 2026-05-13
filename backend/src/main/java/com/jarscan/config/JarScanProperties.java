package com.jarscan.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jarscan")
public record JarScanProperties(
        @NotBlank String appVersion,
        @NotBlank String dataDir,
        @NotBlank String dbPath,
        @NotBlank String dependencyCheckCommand,
        @Min(1) int maxNestedJarDepth,
        @Min(1) long maxExtractedArchiveSizeBytes,
        @Min(1) int maxDuplicateClassScanJars,
        @Min(1) int projectZipMaxFiles,
        @Min(1) long projectZipMaxExtractedSizeBytes,
        @Min(1) int mavenTimeoutSeconds,
        @NotBlank String mavenDependencyScope
) {
}
