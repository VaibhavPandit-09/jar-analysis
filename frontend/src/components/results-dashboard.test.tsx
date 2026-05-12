import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { ResultsDashboard } from "@/components/results-dashboard";
import type { AnalysisResult } from "@/lib/types";

const baseResult: AnalysisResult = {
  jobId: "job-1",
  status: "COMPLETED",
  inputType: "PROJECT_ZIP",
  startedAt: "2026-05-13T00:00:00Z",
  completedAt: "2026-05-13T00:01:00Z",
  summary: {
    totalArtifacts: 1,
    totalDependencies: 0,
    vulnerableDependencies: 0,
    totalVulnerabilities: 0,
    critical: 0,
    high: 0,
    medium: 0,
    low: 0,
    info: 0,
    unknown: 0,
    highestCvss: null,
    requiredJavaVersion: "Java 17",
  },
  artifacts: [
    {
      id: "artifact-1",
      fileName: "app.jar",
      sizeBytes: 1024,
      sha256: "hash",
      entryCount: 2,
      fatJar: false,
      parentPath: null,
      nestedDepth: 0,
      coordinates: { groupId: "com.example", artifactId: "app", version: "1.0.0" },
      javaVersion: { minMajor: 61, maxMajor: 61, requiredJava: "Java 17", multiRelease: false },
      manifest: {
        mainClass: "com.example.Main",
        implementationTitle: null,
        implementationVersion: null,
        implementationVendor: null,
        createdBy: null,
        buildJdk: null,
        automaticModuleName: null,
        multiRelease: null,
        attributes: {},
      },
      moduleType: "CLASSPATH_JAR",
      highestSeverity: "UNKNOWN",
      vulnerabilityCount: 0,
      dependencies: [],
      vulnerabilities: [],
      nestedArtifacts: [],
      rawMetadata: {},
      packagingInspection: {
        packagingType: "PLAIN_ARCHIVE",
        applicationClassesLocation: null,
        dependencyLibrariesLocation: null,
        applicationClassCount: 0,
        dependencyCount: 0,
        nestedLibraryCount: 0,
        nestedLibraries: [],
        largestNestedLibraries: [],
        vulnerableNestedLibraries: [],
        javaVersion: "Java 17",
        springBootVersion: null,
        startClass: null,
        mainClass: "com.example.Main",
        layersIndexPresent: false,
        classpathIndexPresent: false,
        webXmlPresent: false,
        applicationXmlPresent: false,
        webInfLibCount: 0,
        warModuleCount: 0,
        jarModuleCount: 0,
        modulePaths: [],
        springMetadataFiles: [],
        serviceLoaderFiles: [],
        configFiles: [],
        duplicateClassesStatus: "Session 8 duplicate class analysis placeholder",
      },
    },
  ],
  dependencyTreeText: null,
  warnings: [],
  errors: [],
  projectStructure: {
    archiveName: "project.zip",
    rootPomPath: "pom.xml",
    rootPomReason: "pom.xml is at ZIP root",
    pomCount: 1,
    packagedArtifactCount: 1,
    compiledClassDirectoryCount: 1,
    dependencyLibraryDirectoryCount: 0,
    pomFiles: ["pom.xml"],
    moduleDirectories: [],
    compiledClassDirectories: ["target/classes"],
    packagedArtifacts: ["target/app.jar"],
    dependencyLibraryDirectories: [],
    springMetadataFiles: ["META-INF/spring.factories"],
    serviceLoaderFiles: [],
    resourceFiles: ["src/main/resources/application.yml"],
    compiledClassesJavaVersion: { minMajor: 61, maxMajor: 61, requiredJava: "Java 17", multiRelease: false },
  },
};

describe("ResultsDashboard", () => {
  it("renders project structure summary", () => {
    render(<ResultsDashboard result={baseResult} />);

    expect(screen.getByText(/Project structure summary/i)).toBeInTheDocument();
    expect(screen.getAllByText("pom.xml").length).toBeGreaterThan(0);
    expect(screen.getByText("target/app.jar")).toBeInTheDocument();
  });

  it("renders a useful fat jar inspector empty state", async () => {
    render(<ResultsDashboard result={baseResult} />);

    await userEvent.click(screen.getByRole("button", { name: /app\.jar/i }));
    await userEvent.click(screen.getByRole("tab", { name: /Fat JAR Inspector/i }));
    expect(screen.getByText(/not a fat JAR, WAR, or EAR/i)).toBeInTheDocument();
  });
});
