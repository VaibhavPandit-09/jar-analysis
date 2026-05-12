package com.jarscan.dto;

import java.util.List;

public record ProjectStructureSummary(
        String archiveName,
        String rootPomPath,
        String rootPomReason,
        int pomCount,
        int packagedArtifactCount,
        int compiledClassDirectoryCount,
        int dependencyLibraryDirectoryCount,
        List<String> pomFiles,
        List<String> moduleDirectories,
        List<String> compiledClassDirectories,
        List<String> packagedArtifacts,
        List<String> dependencyLibraryDirectories,
        List<String> springMetadataFiles,
        List<String> serviceLoaderFiles,
        List<String> resourceFiles,
        JavaVersionInfo compiledClassesJavaVersion
) {
}
