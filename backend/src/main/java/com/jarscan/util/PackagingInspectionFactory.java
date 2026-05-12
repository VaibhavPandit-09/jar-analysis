package com.jarscan.util;

import com.jarscan.dto.ArtifactAnalysis;
import com.jarscan.dto.MavenCoordinates;
import com.jarscan.dto.NestedLibrarySummary;
import com.jarscan.dto.PackagingInspection;

import java.util.Comparator;
import java.util.List;

public final class PackagingInspectionFactory {

    private PackagingInspectionFactory() {
    }

    public static PackagingInspection create(
            String packagingType,
            String applicationClassesLocation,
            String dependencyLibrariesLocation,
            int applicationClassCount,
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
            String duplicateClassesStatus,
            List<ArtifactAnalysis> nestedArtifacts
    ) {
        List<NestedLibrarySummary> nestedLibraries = nestedArtifacts.stream()
                .map(PackagingInspectionFactory::toSummary)
                .toList();
        return new PackagingInspection(
                packagingType,
                applicationClassesLocation,
                dependencyLibrariesLocation,
                applicationClassCount,
                nestedArtifacts.size(),
                nestedArtifacts.size(),
                nestedLibraries,
                largest(nestedLibraries),
                vulnerable(nestedLibraries),
                javaVersion,
                springBootVersion,
                startClass,
                mainClass,
                layersIndexPresent,
                classpathIndexPresent,
                webXmlPresent,
                applicationXmlPresent,
                webInfLibCount,
                warModuleCount,
                jarModuleCount,
                modulePaths,
                springMetadataFiles,
                serviceLoaderFiles,
                configFiles,
                duplicateClassesStatus
        );
    }

    public static PackagingInspection refresh(PackagingInspection existing, String javaVersion, List<ArtifactAnalysis> nestedArtifacts) {
        if (existing == null) {
            return null;
        }
        List<NestedLibrarySummary> nestedLibraries = nestedArtifacts.stream()
                .map(PackagingInspectionFactory::toSummary)
                .toList();
        return new PackagingInspection(
                existing.packagingType(),
                existing.applicationClassesLocation(),
                existing.dependencyLibrariesLocation(),
                existing.applicationClassCount(),
                nestedArtifacts.size(),
                nestedArtifacts.size(),
                nestedLibraries,
                largest(nestedLibraries),
                vulnerable(nestedLibraries),
                javaVersion,
                existing.springBootVersion(),
                existing.startClass(),
                existing.mainClass(),
                existing.layersIndexPresent(),
                existing.classpathIndexPresent(),
                existing.webXmlPresent(),
                existing.applicationXmlPresent(),
                existing.webInfLibCount(),
                existing.warModuleCount(),
                existing.jarModuleCount(),
                existing.modulePaths(),
                existing.springMetadataFiles(),
                existing.serviceLoaderFiles(),
                existing.configFiles(),
                existing.duplicateClassesStatus()
        );
    }

    private static NestedLibrarySummary toSummary(ArtifactAnalysis artifact) {
        return new NestedLibrarySummary(
                artifact.fileName(),
                artifact.sizeBytes(),
                artifact.javaVersion().requiredJava(),
                artifact.vulnerabilityCount(),
                new MavenCoordinates(
                        artifact.coordinates().groupId(),
                        artifact.coordinates().artifactId(),
                        artifact.coordinates().version()
                )
        );
    }

    private static List<NestedLibrarySummary> largest(List<NestedLibrarySummary> nestedLibraries) {
        return nestedLibraries.stream()
                .sorted(Comparator.comparingLong(NestedLibrarySummary::sizeBytes).reversed())
                .limit(5)
                .toList();
    }

    private static List<NestedLibrarySummary> vulnerable(List<NestedLibrarySummary> nestedLibraries) {
        return nestedLibraries.stream()
                .filter(summary -> summary.vulnerabilityCount() > 0)
                .sorted(Comparator.comparingInt(NestedLibrarySummary::vulnerabilityCount).reversed()
                        .thenComparingLong(NestedLibrarySummary::sizeBytes).reversed())
                .limit(5)
                .toList();
    }
}
