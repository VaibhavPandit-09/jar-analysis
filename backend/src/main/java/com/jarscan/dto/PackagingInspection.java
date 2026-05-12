package com.jarscan.dto;

import java.util.List;

public record PackagingInspection(
        String packagingType,
        String applicationClassesLocation,
        String dependencyLibrariesLocation,
        int applicationClassCount,
        int dependencyCount,
        int nestedLibraryCount,
        List<NestedLibrarySummary> nestedLibraries,
        List<NestedLibrarySummary> largestNestedLibraries,
        List<NestedLibrarySummary> vulnerableNestedLibraries,
        String javaVersion,
        String springBootVersion,
        String startClass,
        String mainClass,
        boolean layersIndexPresent,
        boolean classpathIndexPresent,
        boolean webXmlPresent,
        boolean applicationXmlPresent,
        int webInfLibCount,
        int warModuleCount,
        int jarModuleCount,
        List<String> modulePaths,
        List<String> springMetadataFiles,
        List<String> serviceLoaderFiles,
        List<String> configFiles,
        String duplicateClassesStatus
) {
}
