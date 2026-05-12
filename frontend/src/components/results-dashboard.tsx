import { flexRender, getCoreRowModel, useReactTable } from "@tanstack/react-table";
import { motion } from "framer-motion";
import { Download, Search } from "lucide-react";
import { useMemo, useState } from "react";

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
import { buildJobExportUrl } from "@/lib/api";
import type { ArtifactAnalysis, AnalysisResult, Severity, VulnerabilityFinding } from "@/lib/types";
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

function VulnerabilityTable({ rows }: { rows: VulnerabilityFinding[] }) {
  const table = useReactTable({
    data: rows,
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
    ],
    getCoreRowModel: getCoreRowModel(),
  });

  return (
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
  );
}

interface ResultsDashboardProps {
  result: AnalysisResult;
  exportJobId?: string;
  sourceLabel?: string;
}

export function ResultsDashboard({ result, exportJobId, sourceLabel }: ResultsDashboardProps) {
  const [query, setQuery] = useState("");
  const [severityFilter, setSeverityFilter] = useState<Severity | "ALL">("ALL");

  const filteredArtifacts = useMemo(() => {
    const artifacts = flattenArtifacts(result.artifacts);
    return artifacts.filter((artifact) => {
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
  }, [query, result.artifacts, severityFilter]);

  const exportLinks = exportJobId
    ? [
        ["JSON", "json"],
        ["Markdown", "markdown"],
        ["HTML", "html"],
      ].map(([label, format]) => ({
        label,
        href: buildJobExportUrl(exportJobId, format as "json" | "markdown" | "html"),
        extension: format === "markdown" ? "md" : format,
      }))
    : [];

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="space-y-8">
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
            {filteredArtifacts.map((artifact) => (
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
                      <VulnerabilityTable rows={artifact.vulnerabilities} />
                    </TabsContent>

                    <TabsContent value="metadata">
                      <pre className="overflow-x-auto rounded-3xl border border-border/70 bg-slate-950 p-4 text-xs text-slate-200">
                        {JSON.stringify(artifact.rawMetadata, null, 2)}
                      </pre>
                    </TabsContent>
                  </Tabs>
                </AccordionContent>
              </AccordionItem>
            ))}
          </Accordion>
        </CardContent>
      </Card>

      {result.dependencyTreeText ? (
        <Card>
          <CardHeader>
            <CardTitle>Dependency tree</CardTitle>
            <CardDescription>
              Raw Maven dependency tree output captured during the uploaded POM resolution workflow.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <pre className="overflow-x-auto rounded-3xl border border-border/70 bg-slate-950 p-4 text-xs text-slate-200">
              {result.dependencyTreeText}
            </pre>
          </CardContent>
        </Card>
      ) : null}
    </motion.div>
  );
}
