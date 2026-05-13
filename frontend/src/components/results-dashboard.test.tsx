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
    totalVulnerabilities: 1,
    critical: 0,
    high: 1,
    medium: 0,
    low: 0,
    info: 0,
    unknown: 0,
    highestCvss: 7.5,
    requiredJavaVersion: "Java 17",
    versionConflictCount: 1,
    convergenceIssueCount: 1,
    duplicateClassCount: 1,
    licenseWarningCount: 2,
    permissiveLicenseCount: 0,
    weakCopyleftLicenseCount: 0,
    strongCopyleftLicenseCount: 1,
    unknownLicenseCount: 1,
    multipleLicenseCount: 0,
    apparentlyUnusedDependencyCount: 1,
    possiblyRuntimeUsedDependencyCount: 1,
    slimmingOpportunityCount: 2,
    estimatedRemovableSizeBytes: 4_194_304,
    awsBundleWarningCount: 1,
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
      coordinates: { groupId: "org.example", artifactId: "leaf", version: "1.2.3" },
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
      highestSeverity: "HIGH",
      vulnerabilityCount: 1,
      dependencies: [],
      vulnerabilities: [
        {
          severity: "HIGH",
          cveId: "CVE-2026-0001",
          cvssScore: 7.5,
          packageName: "pkg:maven/org.example/leaf@1.2.3",
          installedVersion: "1.2.3",
          affectedVersionRange: null,
          description: "Example vulnerability",
          references: [],
          source: "dependency-check",
        },
      ],
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
        duplicateClassesStatus: "Best-effort duplicate class analysis is reported at the scan level.",
      },
    },
  ],
  dependencyTree: {
    sourceFormat: "TEXT",
    roots: [
      {
        id: "root",
        groupId: "com.example",
        artifactId: "demo",
        type: "pom",
        classifier: null,
        version: "1.0.0",
        scope: null,
        depth: 0,
        parentId: null,
        direct: false,
        transitive: false,
        omitted: false,
        omittedReason: null,
        conflict: false,
        rawLine: null,
        pathFromRoot: ["com.example:demo:1.0.0"],
        children: [
          {
            id: "parent",
            groupId: "org.example",
            artifactId: "parent",
            type: "jar",
            classifier: null,
            version: "2.0.0",
            scope: "runtime",
            depth: 1,
            parentId: "root",
            direct: true,
            transitive: false,
            omitted: false,
            omittedReason: null,
            conflict: false,
            rawLine: "[INFO] +- org.example:parent:jar:2.0.0:runtime",
            pathFromRoot: ["com.example:demo:1.0.0", "org.example:parent:2.0.0"],
            children: [
              {
                id: "leaf",
                groupId: "org.example",
                artifactId: "leaf",
                type: "jar",
                classifier: null,
                version: "1.2.3",
                scope: "runtime",
                depth: 2,
                parentId: "parent",
                direct: false,
                transitive: true,
                omitted: false,
                omittedReason: null,
                conflict: false,
                rawLine: "[INFO] |  \\- org.example:leaf:jar:1.2.3:runtime",
                pathFromRoot: [
                  "com.example:demo:1.0.0",
                  "org.example:parent:2.0.0",
                  "org.example:leaf:1.2.3",
                ],
                children: [],
              },
            ],
          },
        ],
      },
    ],
  },
  versionConflicts: [
    {
      groupId: "org.example",
      artifactId: "leaf",
      resolvedVersion: "1.2.3",
      requestedVersions: ["1.2.3", "0.9.0"],
      pathsByVersion: {
        "1.2.3": [["com.example:demo:1.0.0", "org.example:parent:2.0.0", "org.example:leaf:1.2.3"]],
        "0.9.0": [["com.example:demo:1.0.0", "org.example:legacy:1.0.0", "org.example:leaf:0.9.0"]],
      },
      conflictType: "NEAREST_WINS_CONFLICT",
      riskLevel: "HIGH",
      recommendation: "Align transitive versions to a single approved release.",
      dependencyManagementSnippet: "<dependencyManagement />",
    },
  ],
  convergenceFindings: [
    {
      groupId: "org.example",
      artifactId: "leaf",
      versionsFound: ["1.2.3", "0.9.0"],
      pathsByVersion: {
        "1.2.3": [["com.example:demo:1.0.0", "org.example:parent:2.0.0", "org.example:leaf:1.2.3"]],
        "0.9.0": [["com.example:demo:1.0.0", "org.example:legacy:1.0.0", "org.example:leaf:0.9.0"]],
      },
      selectedVersion: "1.2.3",
      recommendation: "Converge on one version.",
      snippet: "<dependencyManagement />",
    },
  ],
  duplicateClasses: [
    {
      findingType: "EXACT_DUPLICATE_CLASS",
      symbol: "com.example.Foo.class",
      packageName: "com.example",
      artifacts: ["a.jar", "b.jar"],
      severity: "MEDIUM",
      recommendation: "Exclude one provider.",
      shadowingWarning: "Classpath shadowing warning.",
    },
  ],
  licenses: [
    {
      groupId: "org.example",
      artifactId: "leaf",
      version: "1.2.3",
      licenseName: "GNU Affero General Public License",
      licenseUrl: null,
      source: "EMBEDDED_POM",
      confidence: "HIGH",
      category: "strong copyleft",
      warnings: ["Strong copyleft license detected."],
    },
    {
      groupId: "com.example",
      artifactId: "demo",
      version: "1.0.0",
      licenseName: "Unknown",
      licenseUrl: null,
      source: "NONE",
      confidence: "LOW",
      category: "unknown",
      warnings: ["No embedded license metadata was found."],
    },
  ],
  dependencyUsage: [
    {
      groupId: "org.example",
      artifactId: "leaf",
      version: "1.2.3",
      status: "DECLARED_BUT_UNUSED",
      confidence: "HIGH",
      evidence: ["Maven dependency:analyze reported this dependency as declared but unused."],
      warnings: ["Removal suggestions should be reviewed and tested."],
      suggestedAction: "Review this dependency for removal or exclusion.",
      paths: [["com.example:demo:1.0.0", "org.example:leaf:1.2.3"]],
      sizeBytes: 2_097_152,
      vulnerabilitiesContributed: 1,
    },
    {
      groupId: "software.amazon.awssdk",
      artifactId: "bundle",
      version: "2.25.0",
      status: "POSSIBLY_RUNTIME_USED",
      confidence: "LOW",
      evidence: ["Runtime heuristic: Spring Boot starters and auto-configure modules can be activated without direct bytecode references."],
      warnings: ["Java dependencies may be used through reflection or configuration."],
      suggestedAction: "Review runtime wiring before removing.",
      paths: [["com.example:demo:1.0.0", "software.amazon.awssdk:bundle:2.25.0"]],
      sizeBytes: 2_097_152,
      vulnerabilitiesContributed: 0,
    },
  ],
  slimmingOpportunities: [
    {
      title: "Review declared but unused dependency org.example:leaf:1.2.3",
      dependency: "org.example:leaf:1.2.3",
      opportunityType: "UNUSED_DIRECT_DEPENDENCY",
      sizeImpactBytes: 2_097_152,
      transitiveDependencyCount: 3,
      vulnerabilitiesContributed: 1,
      usageStatus: "DECLARED_BUT_UNUSED",
      confidence: "HIGH",
      evidence: ["This dependency contributes 1 known vulnerability finding."],
      suggestedReplacement: null,
      mavenSnippet: null,
      exclusionSnippet: "<dependency />",
      warnings: ["Review and test before applying exclusions."],
    },
    {
      title: "Replace broad AWS bundle with narrower service modules",
      dependency: "software.amazon.awssdk:bundle:2.25.0",
      opportunityType: "AWS_BUNDLE_REPLACEMENT",
      sizeImpactBytes: null,
      transitiveDependencyCount: null,
      vulnerabilitiesContributed: null,
      usageStatus: "POSSIBLY_RUNTIME_USED",
      confidence: "HIGH",
      evidence: ["A broad AWS SDK bundle was detected."],
      suggestedReplacement: "software.amazon.awssdk:s3",
      mavenSnippet: "<dependency />",
      exclusionSnippet: null,
      warnings: ["Review runtime/configuration usage and test before removing the bundle."],
    },
  ],
  awsBundleAdvice: {
    detectedArtifact: "software.amazon.awssdk:bundle:2.25.0",
    usedServiceModules: ["s3"],
    apparentlyUnusedServiceModules: ["dynamodb", "sqs"],
    suggestedReplacement: "software.amazon.awssdk:s3",
    mavenSnippet: "<dependency />",
    warnings: ["Review runtime/configuration usage and test before removing the bundle."],
    confidence: "HIGH",
  },
  dependencyTreeText: "[INFO] com.example:demo:pom:1.0.0",
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

  it("renders dependency tree tab and selected node details", async () => {
    render(<ResultsDashboard result={baseResult} />);

    await userEvent.click(screen.getByRole("tab", { name: /Dependency Tree/i }));

    expect(screen.getByText(/Parsed Maven dependency graph/i)).toBeInTheDocument();
    expect(screen.getByText(/org\.example:parent:2\.0\.0/i)).toBeInTheDocument();
  });

  it("switches to dependency tree when show path is clicked", async () => {
    render(<ResultsDashboard result={baseResult} />);

    await userEvent.click(screen.getByRole("button", { name: /app\.jar/i }));
    await userEvent.click(screen.getByRole("tab", { name: /Vulnerabilities/i }));
    await userEvent.click(screen.getByRole("button", { name: /Show path/i }));

    expect(screen.getByText(/Why is this dependency here\?/i)).toBeInTheDocument();
    expect(screen.getAllByText(/org\.example:leaf:1\.2\.3/i).length).toBeGreaterThan(0);
  });

  it("renders version conflict tab", async () => {
    render(<ResultsDashboard result={baseResult} />);

    await userEvent.click(screen.getByRole("tab", { name: /Version Conflicts/i }));

    expect(screen.getAllByText(/dependencyManagement snippet/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/NEAREST_WINS_CONFLICT/i)).toBeInTheDocument();
  });

  it("renders duplicate classes and licenses tabs", async () => {
    render(<ResultsDashboard result={baseResult} />);

    await userEvent.click(screen.getByRole("tab", { name: /Duplicate Classes/i }));
    expect(screen.getAllByText(/Classpath shadowing warning/i).length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole("tab", { name: /Licenses/i }));
    expect(screen.getAllByText(/Strong copyleft/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/GNU Affero General Public License/i)).toBeInTheDocument();
  });

  it("renders usage analysis tab and confidence badges", async () => {
    render(<ResultsDashboard result={baseResult} />);

    await userEvent.click(screen.getByRole("tab", { name: /Usage Analysis/i }));

    expect(screen.getByText(/Dependency usage findings are evidence-based/i)).toBeInTheDocument();
    expect(screen.getAllByText(/HIGH/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/DECLARED_BUT_UNUSED/i).length).toBeGreaterThan(0);
  });

  it("renders slimming advisor cards and aws advisor card", async () => {
    render(<ResultsDashboard result={baseResult} />);

    await userEvent.click(screen.getByRole("tab", { name: /Slimming Advisor/i }));

    expect(screen.getByText(/AWS SDK Bundle Detected/i)).toBeInTheDocument();
    expect(screen.getAllByText(/software\.amazon\.awssdk:s3/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Review and test before applying exclusions/i).length).toBeGreaterThan(0);
  });
});
