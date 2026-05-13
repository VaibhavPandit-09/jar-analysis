import { flexRender, getCoreRowModel, useReactTable } from "@tanstack/react-table";
import { motion } from "framer-motion";
import { Download, Search, ShieldBan } from "lucide-react";
import { useMemo, useState } from "react";
import { toast } from "sonner";

import { DependencyTreePanel, type DependencyTreeFocusRequest, type DependencyVulnerabilitySummary } from "@/components/dependency-tree-panel";
import { DuplicateClassesPanel, LicensesPanel, PolicyResultsPanel, SlimmingAdvisorPanel, UsageAnalysisPanel, VersionConflictsPanel } from "@/components/insights-panels";
import { SuppressionDialog } from "@/components/suppression-dialog";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { buildJobExportUrl, buildScanExportUrl, createSuppression } from "@/lib/api";
import { buildDependencyTreeIndex, dependencyCoordinateKey, dependencyKey } from "@/lib/dependency-tree";
import type { SuppressionDraft } from "@/lib/suppressions";
import type {
  AnalysisResult,
  ArtifactAnalysis,
  MavenCoordinates,
  NestedLibrarySummary,
  ProjectStructureSummary,
  Severity,
  VulnerabilityFinding,
} from "@/lib/types";
import { cn } from "@/lib/utils";

const severityVariant: Record<Severity, "critical" | "high" | "medium" | "low" | "info" | "neutral"> = {
  CRITICAL: "critical",
  HIGH: "high",
  MEDIUM: "medium",
  LOW: "low",
  INFO: "info",
  UNKNOWN: "neutral",
};

function flattenArtifacts(artifacts: ArtifactAnalysis[]): ArtifactAnalysis[] {
  return artifacts.flatMap((artifact) => [artifact, ...flattenArtifacts(artifact.nestedArtifacts)]);
}

function formatBytes(bytes: number) {
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

function formatCoordinates(artifact: { groupId: string | null; artifactId: string | null; version: string | null }) {
  return `${artifact.groupId ?? "unknown"}:${artifact.artifactId ?? "unknown"}:${artifact.version ?? "unknown"}`;
}

function NestedLibraryTable({ libraries }: { libraries: NestedLibrarySummary[] }) {
  return (
    <div className="overflow-hidden rounded-3xl border border-border/70">
      <table className="min-w-full divide-y divide-border/70 text-sm">
        <thead className="bg-secondary/60 text-left">
          <tr>
            {["Library", "Coordinates", "Size", "Java", "Findings"].map((header) => (
              <th key={header} className="px-4 py-3 font-medium text-muted-foreground">{header}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border/60 bg-background/70">
          {libraries.map((library) => (
            <tr key={`${library.fileName}-${library.sizeBytes}`}>
              <td className="px-4 py-3">{library.fileName}</td>
              <td className="px-4 py-3 text-muted-foreground">{formatCoordinates(library.coordinates)}</td>
              <td className="px-4 py-3">{formatBytes(library.sizeBytes)}</td>
              <td className="px-4 py-3">{library.javaVersion}</td>
              <td className="px-4 py-3">{library.vulnerabilityCount}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ProjectStructurePanel({ projectStructure }: { projectStructure: ProjectStructureSummary }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Project structure summary</CardTitle>
        <CardDescription>
          Best-effort ZIP structure detection, root POM heuristics, packaged archives, compiled classes, and metadata evidence.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
          {[
            ["Root POM", projectStructure.rootPomPath ?? "Not detected"],
            ["POM files", projectStructure.pomCount],
            ["Packaged artifacts", projectStructure.packagedArtifactCount],
            ["Class dirs", projectStructure.compiledClassDirectoryCount],
            ["Lib dirs", projectStructure.dependencyLibraryDirectoryCount],
            ["Compiled Java", projectStructure.compiledClassesJavaVersion.requiredJava],
          ].map(([label, value]) => (
            <div key={String(label)} className="rounded-2xl border border-border/70 bg-background/60 px-4 py-4">
              <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{label}</div>
              <div className="mt-2 text-sm font-medium break-words">{value}</div>
            </div>
          ))}
        </div>
        <div className="grid gap-4 lg:grid-cols-2">
          {[
            ["Detected POMs", projectStructure.pomFiles],
            ["Modules", projectStructure.moduleDirectories],
            ["Packaged artifacts", projectStructure.packagedArtifacts],
            ["Dependency lib dirs", projectStructure.dependencyLibraryDirectories],
            ["Spring metadata", projectStructure.springMetadataFiles],
            ["ServiceLoader metadata", projectStructure.serviceLoaderFiles],
          ].map(([label, rows]) => (
            <div key={String(label)} className="rounded-2xl border border-border/70 bg-background/60 px-4 py-4">
              <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{label}</div>
              <div className="mt-3 space-y-1 text-sm text-muted-foreground">
                {Array.isArray(rows) && rows.length > 0 ? rows.map((row) => <div key={row}>{row}</div>) : <div>None detected</div>}
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

function FatJarInspectorTab({ artifact }: { artifact: ArtifactAnalysis }) {
  const inspection = artifact.packagingInspection;
  if (!inspection || inspection.packagingType === "PLAIN_ARCHIVE") {
    return (
      <div className="rounded-2xl border border-border/70 bg-background/60 px-4 py-5 text-sm text-muted-foreground">
        This artifact is not a fat JAR, WAR, or EAR with an inspectable bundled-dependency layout.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="grid gap-3 lg:grid-cols-2 xl:grid-cols-4">
        {[
          ["Packaging type", inspection.packagingType],
          ["App classes", inspection.applicationClassesLocation ?? "n/a"],
          ["Dependency libs", inspection.dependencyLibrariesLocation ?? "n/a"],
          ["Dependency count", inspection.dependencyCount],
          ["Nested libs", inspection.nestedLibraryCount],
          ["Java", inspection.javaVersion],
          ["Spring Boot", inspection.springBootVersion ?? "n/a"],
          ["Start-Class", inspection.startClass ?? inspection.mainClass ?? "n/a"],
        ].map(([label, value]) => (
          <div key={String(label)} className="rounded-2xl border border-border/70 bg-background/60 px-4 py-4">
            <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{label}</div>
            <div className="mt-2 text-sm font-medium break-words">{value}</div>
          </div>
        ))}
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="rounded-2xl border border-border/70 bg-background/60 px-4 py-4">
          <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Largest libs</div>
          <div className="mt-3 space-y-1 text-sm text-muted-foreground">
            {inspection.largestNestedLibraries.length > 0
              ? inspection.largestNestedLibraries.map((item) => <div key={item.fileName}>{item.fileName} ({item.vulnerabilityCount} findings)</div>)
              : <div>None</div>}
          </div>
        </div>
        <div className="rounded-2xl border border-border/70 bg-background/60 px-4 py-4">
          <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Vulnerable libs</div>
          <div className="mt-3 space-y-1 text-sm text-muted-foreground">
            {inspection.vulnerableNestedLibraries.length > 0
              ? inspection.vulnerableNestedLibraries.map((item) => <div key={item.fileName}>{item.fileName} ({item.vulnerabilityCount} findings)</div>)
              : <div>None</div>}
          </div>
        </div>
        <div className="rounded-2xl border border-border/70 bg-background/60 px-4 py-4">
          <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Modules</div>
          <div className="mt-3 space-y-1 text-sm text-muted-foreground">
            {inspection.modulePaths.length > 0
              ? inspection.modulePaths.map((item) => <div key={item}>{item}</div>)
              : <div>None</div>}
          </div>
        </div>
      </div>

      {inspection.nestedLibraries.length > 0 ? <NestedLibraryTable libraries={inspection.nestedLibraries} /> : null}
    </div>
  );
}

function VulnerabilityTable({
  rows,
  onShowPath,
  onSuppress,
}: {
  rows: VulnerabilityFinding[];
  onShowPath?: (() => void) | null;
  onSuppress?: ((finding: VulnerabilityFinding) => void) | null;
}) {
  const [showSuppressed, setShowSuppressed] = useState(false);
  const visibleRows = useMemo(
    () => (showSuppressed ? rows : rows.filter((row) => !row.suppressed)),
    [rows, showSuppressed],
  );
  const table = useReactTable({
    data: visibleRows,
    columns: [
      {
        header: "Severity",
        cell: ({ row }) => <Badge variant={severityVariant[row.original.severity]}>{row.original.severity}</Badge>,
      },
      {
        header: "Identifier",
        accessorKey: "cveId",
      },
      {
        header: "CVSS",
        cell: ({ row }) => row.original.cvssScore?.toFixed(1) ?? "n/a",
      },
      {
        header: "Package",
        cell: ({ row }) => row.original.packageName ?? "Unknown package",
      },
      {
        header: "Installed",
        accessorKey: "installedVersion",
      },
      {
        header: "Description",
        cell: ({ row }) => (
          <div className="max-w-xl text-sm text-muted-foreground">
            {row.original.description ?? "No description available"}
          </div>
        ),
      },
      {
        header: "Path",
        cell: () => onShowPath ? (
          <button type="button" className="text-sm font-medium text-primary hover:underline" onClick={onShowPath}>
            Show path
          </button>
        ) : <span className="text-muted-foreground">n/a</span>,
      },
      {
        header: "Actions",
        cell: ({ row }) => row.original.suppressed ? (
          <div className="text-xs text-sky-700 dark:text-sky-200">
            Suppressed{row.original.suppressionReason ? `: ${row.original.suppressionReason}` : ""}
          </div>
        ) : onSuppress ? (
          <button type="button" className="inline-flex items-center text-sm font-medium text-primary hover:underline" onClick={() => onSuppress(row.original)}>
            <ShieldBan className="mr-1 h-4 w-4" /> Suppress
          </button>
        ) : <span className="text-muted-foreground">n/a</span>,
      },
    ],
    getCoreRowModel: getCoreRowModel(),
  });

  return (
    <div className="space-y-3">
      <div className="flex justify-end">
        <button type="button" className="text-sm font-medium text-primary hover:underline" onClick={() => setShowSuppressed((current) => !current)}>
          {showSuppressed ? "Hide suppressed findings" : `Show suppressed findings (${rows.filter((row) => row.suppressed).length})`}
        </button>
      </div>
      <div className="overflow-hidden rounded-3xl border border-border/70">
      <table className="min-w-full divide-y divide-border/70 text-sm">
        <thead className="bg-secondary/60 text-left">
          {table.getHeaderGroups().map((group) => (
            <tr key={group.id}>
              {group.headers.map((header) => (
                <th key={header.id} className="px-4 py-3 font-medium text-muted-foreground">
                  {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody className="divide-y divide-border/60 bg-background/70">
          {table.getRowModel().rows.map((row) => (
            <tr key={row.id}>
              {row.getVisibleCells().map((cell) => (
                <td key={cell.id} className="px-4 py-3 align-top">
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </div>
  );
}

interface ResultsDashboardProps {
  result: AnalysisResult;
  exportJobId?: string;
  sourceLabel?: string;
  scanId?: string;
  onRefresh?: () => Promise<void> | void;
}

export function ResultsDashboard({ result, exportJobId, sourceLabel, scanId, onRefresh }: ResultsDashboardProps) {
  const [query, setQuery] = useState("");
  const [severityFilter, setSeverityFilter] = useState<Severity | "ALL">("ALL");
  const [activeResultsTab, setActiveResultsTab] = useState<"artifacts" | "dependency-tree" | "version-conflicts" | "duplicate-classes" | "licenses" | "usage-analysis" | "slimming-advisor" | "policy-results" | "exports">("artifacts");
  const [dependencyTreeFocus, setDependencyTreeFocus] = useState<DependencyTreeFocusRequest | null>(null);
  const [suppressionDraft, setSuppressionDraft] = useState<SuppressionDraft | null>(null);

  const allArtifacts = useMemo(() => flattenArtifacts(result.artifacts), [result.artifacts]);
  const dependencyTreeIndex = useMemo(() => buildDependencyTreeIndex(result.dependencyTree), [result.dependencyTree]);

  const filteredArtifacts = useMemo(() => {
    return allArtifacts.filter((artifact) => {
      const matchesQuery =
        !query ||
        artifact.fileName.toLowerCase().includes(query.toLowerCase()) ||
        `${artifact.coordinates.groupId ?? ""}:${artifact.coordinates.artifactId ?? ""}:${artifact.coordinates.version ?? ""}`
          .toLowerCase()
          .includes(query.toLowerCase());
      const matchesSeverity =
        severityFilter === "ALL" ||
        artifact.highestSeverity === severityFilter ||
        artifact.vulnerabilities.some((item) => item.severity === severityFilter);
      return matchesQuery && matchesSeverity;
    });
  }, [allArtifacts, query, severityFilter]);

  const dependencyVulnerabilityIndex = useMemo(() => {
    const index = new Map<string, DependencyVulnerabilitySummary>();
    allArtifacts.forEach((artifact) => {
      if (!artifact.coordinates.groupId || !artifact.coordinates.artifactId || !artifact.coordinates.version) {
        return;
      }
      const key = dependencyCoordinateKey(artifact.coordinates);
      const existing = index.get(key);
      if (existing) {
        existing.count += artifact.vulnerabilities.length;
        existing.vulnerabilities.push(...artifact.vulnerabilities);
        return;
      }
      index.set(key, {
        count: artifact.vulnerabilities.length,
        vulnerabilities: [...artifact.vulnerabilities],
      });
    });
    return index;
  }, [allArtifacts]);

  const exportLinks = exportJobId
    ? [
        ["JSON", "json"],
        ["Markdown", "markdown"],
        ["HTML", "html"],
        ...(scanId ? [["CycloneDX JSON", "cyclonedx-json"]] : []),
      ].map(([label, format]) => ({
        label,
        href: scanId
          ? buildScanExportUrl(scanId, format as "json" | "markdown" | "html" | "cyclonedx-json")
          : buildJobExportUrl(exportJobId, format as "json" | "markdown" | "html"),
        extension: format === "markdown" ? "md" : format === "cyclonedx-json" ? "cdx.json" : format,
      }))
    : [];

  const focusDependencyInTree = (coordinates: MavenCoordinates) => {
    if (!coordinates.groupId || !coordinates.artifactId) {
      return;
    }
    setActiveResultsTab("dependency-tree");
    setDependencyTreeFocus({
      groupId: coordinates.groupId,
      artifactId: coordinates.artifactId,
      version: coordinates.version,
      token: Date.now(),
    });
  };

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="space-y-8">
      {result.projectStructure ? <ProjectStructurePanel projectStructure={result.projectStructure} /> : null}

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {[
          ["Artifacts", result.summary.totalArtifacts],
          ["Dependencies", result.summary.totalDependencies],
          ["Vulnerabilities", result.summary.totalVulnerabilities],
          ["Required Java", result.summary.requiredJavaVersion],
          ["Highest CVSS", result.summary.highestCvss?.toFixed(1) ?? "n/a"],
        ].map(([label, value]) => (
          <Card key={String(label)}>
            <CardContent className="p-5">
              <div className="text-sm text-muted-foreground">{label}</div>
              <div className="mt-2 font-display text-3xl font-semibold tracking-tight">{value}</div>
            </CardContent>
          </Card>
        ))}
      </section>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-6">
        {[
          ["Version conflicts", result.summary.versionConflictCount],
          ["Convergence issues", result.summary.convergenceIssueCount],
          ["Duplicate class findings", result.summary.duplicateClassCount],
          ["License warnings", result.summary.licenseWarningCount],
          ["Unknown licenses", result.summary.unknownLicenseCount],
          ["Strong copyleft", result.summary.strongCopyleftLicenseCount],
        ].map(([label, value]) => (
          <Card key={String(label)}>
            <CardContent className="p-5">
              <div className="text-sm text-muted-foreground">{label}</div>
              <div className="mt-2 font-display text-3xl font-semibold tracking-tight">{value}</div>
            </CardContent>
          </Card>
        ))}
      </section>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-6">
        {[
          ["Critical", result.summary.critical, "critical"],
          ["High", result.summary.high, "high"],
          ["Medium", result.summary.medium, "medium"],
          ["Low", result.summary.low, "low"],
          ["Info", result.summary.info, "info"],
          ["Unknown", result.summary.unknown, "neutral"],
        ].map(([label, value, variant]) => (
          <Card key={String(label)}>
            <CardContent className="p-5">
              <Badge variant={variant as never}>{label}</Badge>
              <div className="mt-3 font-display text-3xl font-semibold tracking-tight">{value}</div>
            </CardContent>
          </Card>
        ))}
      </section>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {[
          ["Apparently unused", result.summary.apparentlyUnusedDependencyCount],
          ["Possibly runtime-used", result.summary.possiblyRuntimeUsedDependencyCount],
          ["Slimming opportunities", result.summary.slimmingOpportunityCount],
          ["Estimated removable size", formatBytes(result.summary.estimatedRemovableSizeBytes)],
          ["AWS bundle warnings", result.summary.awsBundleWarningCount],
        ].map(([label, value]) => (
          <Card key={String(label)}>
            <CardContent className="p-5">
              <div className="text-sm text-muted-foreground">{label}</div>
              <div className="mt-2 font-display text-3xl font-semibold tracking-tight">{value}</div>
            </CardContent>
          </Card>
        ))}
      </section>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {[
          ["Policy warnings", result.summary.policyWarningCount],
          ["Policy failures", result.summary.policyFailureCount],
          ["Overall policy", result.summary.overallPolicyStatus ?? "n/a"],
        ].map(([label, value]) => (
          <Card key={String(label)}>
            <CardContent className="p-5">
              <div className="text-sm text-muted-foreground">{label}</div>
              <div className="mt-2 font-display text-3xl font-semibold tracking-tight">{value}</div>
            </CardContent>
          </Card>
        ))}
      </section>

      <Tabs value={activeResultsTab} onValueChange={(value) => setActiveResultsTab(value as "artifacts" | "dependency-tree" | "version-conflicts" | "duplicate-classes" | "licenses" | "usage-analysis" | "slimming-advisor" | "policy-results" | "exports")}>
        <TabsList>
          <TabsTrigger value="artifacts">Artifacts</TabsTrigger>
          <TabsTrigger value="dependency-tree">Dependency Tree</TabsTrigger>
          <TabsTrigger value="usage-analysis">Usage Analysis</TabsTrigger>
          <TabsTrigger value="slimming-advisor">Slimming Advisor</TabsTrigger>
          <TabsTrigger value="version-conflicts">Version Conflicts</TabsTrigger>
          <TabsTrigger value="duplicate-classes">Duplicate Classes</TabsTrigger>
          <TabsTrigger value="licenses">Licenses</TabsTrigger>
          <TabsTrigger value="policy-results">Policy Results</TabsTrigger>
          <TabsTrigger value="exports">Exports</TabsTrigger>
        </TabsList>

        <TabsContent value="artifacts">
          <Card>
            <CardHeader className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <CardTitle>Results dashboard</CardTitle>
                <CardDescription>
                  {sourceLabel
                    ? `${sourceLabel}. Filter by artifact name, Maven coordinates, or severity to focus on the riskiest parts of the graph.`
                    : "Filter by artifact name, Maven coordinates, or severity to focus on the riskiest parts of the graph."}
                </CardDescription>
              </div>
              <div className="flex flex-wrap gap-3">
                <div className="flex items-center gap-2 rounded-full border border-border bg-background px-4 py-2">
                  <Search className="h-4 w-4 text-muted-foreground" />
                  <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    className="bg-transparent text-sm outline-none placeholder:text-muted-foreground"
                    placeholder="Search artifacts"
                  />
                </div>
                <select
                  value={severityFilter}
                  onChange={(event) => setSeverityFilter(event.target.value as Severity | "ALL")}
                  className="rounded-full border border-border bg-background px-4 py-2 text-sm outline-none"
                >
                  {["ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO", "UNKNOWN"].map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
                {exportLinks.map((link) => (
                  <a
                    key={link.label}
                    href={link.href}
                    download={`jarscan-${exportJobId}.${link.extension}`}
                    className={cn(buttonVariants({ variant: "outline" }))}
                  >
                    {link.label === "JSON" ? <Download className="mr-2 h-4 w-4" /> : null}
                    Export {link.label}
                  </a>
                ))}
              </div>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="flex flex-wrap gap-2">
                {(["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO", "UNKNOWN"] as Severity[]).map((severity) => (
                  <Badge key={severity} variant={severityVariant[severity]}>
                    {severity}
                  </Badge>
                ))}
              </div>

              <Accordion type="multiple" className="space-y-4">
                {filteredArtifacts.map((artifact) => {
                  const pathAvailable = Boolean(
                    result.dependencyTree &&
                    artifact.coordinates.groupId &&
                    artifact.coordinates.artifactId &&
                    dependencyTreeIndex.byKey.has(dependencyKey(artifact.coordinates)),
                  );

                  return (
                    <AccordionItem key={artifact.id} value={artifact.id}>
                      <AccordionTrigger>
                        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-3">
                          <span className="truncate font-medium">{artifact.fileName}</span>
                          <Badge variant={severityVariant[artifact.highestSeverity]}>{artifact.highestSeverity}</Badge>
                          <Badge variant="neutral">{artifact.javaVersion.requiredJava}</Badge>
                          <Badge variant="neutral">{artifact.vulnerabilityCount} findings</Badge>
                        </div>
                      </AccordionTrigger>
                      <AccordionContent>
                        <Tabs defaultValue="overview">
                          <TabsList>
                            <TabsTrigger value="overview">Overview</TabsTrigger>
                            <TabsTrigger value="fat-jar-inspector">Fat JAR Inspector</TabsTrigger>
                            <TabsTrigger value="manifest">Manifest</TabsTrigger>
                            <TabsTrigger value="dependencies">Dependencies</TabsTrigger>
                            <TabsTrigger value="vulnerabilities">Vulnerabilities</TabsTrigger>
                            <TabsTrigger value="metadata">Raw Metadata</TabsTrigger>
                          </TabsList>

                          <TabsContent value="overview" className="grid gap-4 lg:grid-cols-2">
                            {[
                              ["Coordinates", `${artifact.coordinates.groupId ?? "unknown"}:${artifact.coordinates.artifactId ?? "unknown"}:${artifact.coordinates.version ?? "unknown"}`],
                              ["SHA-256", artifact.sha256],
                              ["Size", formatBytes(artifact.sizeBytes)],
                              ["Entries", artifact.entryCount],
                              ["Module type", artifact.moduleType],
                              ["Fat JAR", artifact.fatJar ? "Yes" : "No"],
                            ].map(([label, value]) => (
                              <div key={String(label)} className="rounded-2xl border border-border/70 bg-background/60 px-4 py-4">
                                <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{label}</div>
                                <div className="mt-2 break-all text-sm font-medium">{value}</div>
                              </div>
                            ))}
                          </TabsContent>

                          <TabsContent value="fat-jar-inspector">
                            <FatJarInspectorTab artifact={artifact} />
                          </TabsContent>

                          <TabsContent value="manifest">
                            <div className="grid gap-3 sm:grid-cols-2">
                              {Object.entries(artifact.manifest.attributes).map(([key, value]) => (
                                <div key={key} className="rounded-2xl border border-border/70 bg-background/60 px-4 py-3">
                                  <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{key}</div>
                                  <div className="mt-2 text-sm">{value}</div>
                                </div>
                              ))}
                              {Object.keys(artifact.manifest.attributes).length === 0 ? (
                                <div className="rounded-2xl border border-border/70 bg-background/60 px-4 py-5 text-sm text-muted-foreground">
                                  No manifest attributes were available for this artifact.
                                </div>
                              ) : null}
                            </div>
                          </TabsContent>

                          <TabsContent value="dependencies">
                            <div className="overflow-hidden rounded-3xl border border-border/70">
                              <table className="min-w-full divide-y divide-border/70 text-sm">
                                <thead className="bg-secondary/60 text-left">
                                  <tr>
                                    {["Artifact", "Coordinates", "Scope", "Java", "Findings"].map((header) => (
                                      <th key={header} className="px-4 py-3 font-medium text-muted-foreground">
                                        {header}
                                      </th>
                                    ))}
                                  </tr>
                                </thead>
                                <tbody className="divide-y divide-border/60 bg-background/70">
                                  {artifact.dependencies.map((dependency) => (
                                    <tr key={`${dependency.artifact}-${dependency.coordinates.version}`}>
                                      <td className="px-4 py-3">{dependency.artifact}</td>
                                      <td className="px-4 py-3 text-muted-foreground">
                                        {dependency.coordinates.groupId ?? "unknown"}:
                                        {dependency.coordinates.artifactId ?? "unknown"}:
                                        {dependency.coordinates.version ?? "unknown"}
                                      </td>
                                      <td className="px-4 py-3">{dependency.scope ?? "n/a"}</td>
                                      <td className="px-4 py-3">{dependency.javaVersion ?? "Unknown"}</td>
                                      <td className="px-4 py-3">{dependency.vulnerabilityCount}</td>
                                    </tr>
                                  ))}
                                  {artifact.dependencies.length === 0 ? (
                                    <tr>
                                      <td colSpan={5} className="px-4 py-5 text-muted-foreground">
                                        No embedded dependencies were detected for this artifact.
                                      </td>
                                    </tr>
                                  ) : null}
                                </tbody>
                              </table>
                            </div>
                          </TabsContent>

                          <TabsContent value="vulnerabilities">
                            <VulnerabilityTable
                              rows={artifact.vulnerabilities}
                              onShowPath={pathAvailable ? () => focusDependencyInTree(artifact.coordinates) : null}
                              onSuppress={scanId ? (finding) => setSuppressionDraft({
                                title: "Suppress vulnerability finding",
                                description: "Keep the raw vulnerability record, but hide it from the default review surface until the suppression is removed or expires.",
                                targetLabel: `${artifact.coordinates.groupId ?? "unknown"}:${artifact.coordinates.artifactId ?? "unknown"}:${finding.cveId ?? finding.packageName ?? "vulnerability"}`,
                                payload: {
                                  type: "VULNERABILITY",
                                  groupId: artifact.coordinates.groupId,
                                  artifactId: artifact.coordinates.artifactId,
                                  version: artifact.coordinates.version ?? finding.installedVersion,
                                  cveId: finding.cveId,
                                },
                              }) : null}
                            />
                          </TabsContent>

                          <TabsContent value="metadata">
                            <pre className="overflow-x-auto rounded-3xl border border-border/70 bg-slate-950 p-4 text-xs text-slate-200">
                              {JSON.stringify(artifact.rawMetadata, null, 2)}
                            </pre>
                          </TabsContent>
                        </Tabs>
                      </AccordionContent>
                    </AccordionItem>
                  );
                })}
              </Accordion>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="dependency-tree">
          {result.dependencyTree && result.dependencyTree.roots.length > 0 ? (
            <DependencyTreePanel
              tree={result.dependencyTree}
              dependencyTreeText={result.dependencyTreeText}
              vulnerabilityIndex={dependencyVulnerabilityIndex}
              focusRequest={dependencyTreeFocus}
            />
          ) : (
            <Card>
              <CardHeader>
                <CardTitle>Dependency tree unavailable</CardTitle>
                <CardDescription>
                  JARScan can only reconstruct a full Maven graph when Maven tree output is available from an uploaded `pom.xml` or a project ZIP with a usable root `pom.xml`.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3 text-sm text-muted-foreground">
                <div>Standalone JAR, WAR, and EAR uploads can still surface packaged libraries, manifests, Java version evidence, and vulnerabilities.</div>
                <div>Upload `pom.xml` or a project ZIP to capture a complete dependency tree and explain why a transitive dependency is present.</div>
                {result.dependencyTreeText ? (
                  <details className="rounded-3xl border border-border/70 bg-background/70 px-4 py-4">
                    <summary className="cursor-pointer font-medium text-foreground">Show raw Maven tree output</summary>
                    <pre className="mt-4 overflow-x-auto rounded-2xl bg-slate-950 p-4 text-xs text-slate-200">
                      {result.dependencyTreeText}
                    </pre>
                  </details>
                ) : null}
              </CardContent>
            </Card>
          )}
        </TabsContent>

        <TabsContent value="version-conflicts">
          {result.dependencyTree && result.dependencyTree.roots.length > 0 ? (
            <VersionConflictsPanel
              versionConflicts={result.versionConflicts}
              convergenceFindings={result.convergenceFindings}
              onSuppress={scanId ? setSuppressionDraft : undefined}
            />
          ) : (
            <Card>
              <CardHeader>
                <CardTitle>Version conflict analysis unavailable</CardTitle>
                <CardDescription>
                  Version conflict and convergence analysis depend on the parsed Maven dependency tree from an uploaded `pom.xml` or a project ZIP with a usable root `pom.xml`.
                </CardDescription>
              </CardHeader>
            </Card>
          )}
        </TabsContent>

        <TabsContent value="duplicate-classes">
          <DuplicateClassesPanel findings={result.duplicateClasses} onSuppress={scanId ? setSuppressionDraft : undefined} />
        </TabsContent>

        <TabsContent value="licenses">
          <LicensesPanel licenses={result.licenses} onSuppress={scanId ? setSuppressionDraft : undefined} />
        </TabsContent>

        <TabsContent value="usage-analysis">
          <UsageAnalysisPanel findings={result.dependencyUsage} onSuppress={scanId ? setSuppressionDraft : undefined} />
        </TabsContent>

        <TabsContent value="slimming-advisor">
          <SlimmingAdvisorPanel opportunities={result.slimmingOpportunities} awsBundleAdvice={result.awsBundleAdvice} />
        </TabsContent>

        <TabsContent value="policy-results">
          <PolicyResultsPanel evaluation={result.policyEvaluation ?? null} onSuppress={scanId ? setSuppressionDraft : undefined} />
        </TabsContent>

        <TabsContent value="exports">
          <Card>
            <CardHeader>
              <CardTitle>Exports</CardTitle>
              <CardDescription>
                Export the current result in JSON, Markdown, HTML, or CycloneDX JSON where supported.
              </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-wrap gap-3">
              {exportLinks.length > 0 ? exportLinks.map((link) => (
                <a
                  key={link.label}
                  href={link.href}
                  download={`jarscan-${scanId ?? exportJobId}.${link.extension}`}
                  className={cn(buttonVariants({ variant: "outline" }))}
                >
                  <Download className="mr-2 h-4 w-4" />
                  Export {link.label}
                </a>
              )) : (
                <div className="rounded-3xl border border-border/70 bg-background/70 px-4 py-5 text-sm text-muted-foreground">
                  Export links are unavailable for this result.
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <SuppressionDialog
        open={Boolean(suppressionDraft)}
        title={suppressionDraft?.title ?? ""}
        description={suppressionDraft?.description ?? ""}
        targetLabel={suppressionDraft?.targetLabel ?? ""}
        initialPayload={suppressionDraft?.payload ?? { type: "DEPENDENCY" }}
        onClose={() => setSuppressionDraft(null)}
        onSubmit={async (payload) => {
          if (!scanId) {
            toast.error("Suppressions are only available for persisted scans.");
            return;
          }
          await createSuppression(payload);
          toast.success("Suppression created");
          await onRefresh?.();
        }}
      />
    </motion.div>
  );
}
