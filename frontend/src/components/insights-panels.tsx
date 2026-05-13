import { Copy, Layers3, Scale, TrendingDown } from "lucide-react";
import { useMemo, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type {
  AwsBundleAdvice,
  ConvergenceFinding,
  DependencyUsageFinding,
  DuplicateClassFinding,
  LicenseFinding,
  SlimmingOpportunity,
  VersionConflictFinding,
} from "@/lib/types";

export function VersionConflictsPanel({
  versionConflicts,
  convergenceFindings,
}: {
  versionConflicts: VersionConflictFinding[];
  convergenceFindings: ConvergenceFinding[];
}) {
  if (versionConflicts.length === 0 && convergenceFindings.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Version conflicts</CardTitle>
          <CardDescription>No version conflicts or convergence issues were detected in the parsed dependency tree.</CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Version conflicts</CardTitle>
          <CardDescription>
            These findings come from the parsed Maven dependency tree. They show where multiple versions were requested, which version won, and a dependencyManagement snippet you can copy into a POM review if you want to pin the selected version explicitly.
          </CardDescription>
        </CardHeader>
      </Card>

      <div className="space-y-4">
        {versionConflicts.map((conflict) => (
          <Card key={`${conflict.groupId}:${conflict.artifactId}`}>
            <CardContent className="space-y-5 p-6">
              <div className="flex flex-wrap items-center gap-3">
                <div>
                  <div className="text-lg font-semibold">{conflict.groupId}:{conflict.artifactId}</div>
                  <div className="text-sm text-muted-foreground">Resolved version: {conflict.resolvedVersion ?? "unknown"}</div>
                </div>
                <Badge variant={riskVariant(conflict.riskLevel)}>{conflict.riskLevel} risk</Badge>
                <Badge variant="neutral">{conflict.conflictType}</Badge>
              </div>

              <div className="grid gap-4 lg:grid-cols-2">
                <InfoBlock title="Requested versions">{conflict.requestedVersions.join(", ")}</InfoBlock>
                <InfoBlock title="Recommendation">{conflict.recommendation}</InfoBlock>
              </div>

              <div className="space-y-3">
                <div className="text-sm font-medium">Introducing paths</div>
                {Object.entries(conflict.pathsByVersion).map(([version, paths]) => (
                  <div key={version} className="rounded-3xl border border-border/70 bg-background/70 px-4 py-4">
                    <div className="mb-3 flex items-center gap-2">
                      <Badge variant="neutral">{version}</Badge>
                      <span className="text-sm text-muted-foreground">{paths.length} path{paths.length === 1 ? "" : "s"}</span>
                    </div>
                    <div className="space-y-3 text-sm">
                      {paths.map((path, index) => (
                        <div key={`${version}-${index}`} className="rounded-2xl border border-border/60 bg-background px-3 py-3">
                          {path.map((step, stepIndex) => (
                            <div key={`${step}-${stepIndex}`} className="flex items-start gap-2">
                              <span className="mt-0.5 text-xs text-muted-foreground">{stepIndex === 0 ? "root" : "└──"}</span>
                              <span className="break-all">{step}</span>
                            </div>
                          ))}
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>

              {conflict.dependencyManagementSnippet ? (
                <SnippetCard title="dependencyManagement snippet" snippet={conflict.dependencyManagementSnippet} />
              ) : null}
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Dependency convergence</CardTitle>
          <CardDescription>
            Convergence findings focus on dependencies that appear with more than one version anywhere in the parsed tree, even when Maven selected one version successfully.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {convergenceFindings.map((finding) => (
            <div key={`${finding.groupId}:${finding.artifactId}`} className="rounded-3xl border border-border/70 bg-background/70 px-4 py-4">
              <div className="flex flex-wrap items-center gap-2">
                <div className="font-medium">{finding.groupId}:{finding.artifactId}</div>
                <Badge variant="medium">selected {finding.selectedVersion ?? "unknown"}</Badge>
              </div>
              <div className="mt-2 text-sm text-muted-foreground">Versions found: {finding.versionsFound.join(", ")}</div>
              <div className="mt-3 text-sm">{finding.recommendation}</div>
              {finding.snippet ? <SnippetCard title="Suggested snippet" snippet={finding.snippet} /> : null}
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}

export function DuplicateClassesPanel({ findings }: { findings: DuplicateClassFinding[] }) {
  if (findings.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Duplicate classes</CardTitle>
          <CardDescription>No duplicate classes, split packages, or known duplicate-provider patterns were detected in the scanned archive set.</CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Duplicate classes</CardTitle>
          <CardDescription>
            These findings highlight exact duplicate classes, split packages, and a few high-signal runtime pattern collisions that often lead to classpath shadowing or unpredictable behavior.
          </CardDescription>
        </CardHeader>
      </Card>

      {findings.map((finding, index) => (
        <Card key={`${finding.findingType}-${finding.symbol}-${index}`}>
          <CardContent className="space-y-4 p-6">
            <div className="flex flex-wrap items-center gap-3">
              <div>
                <div className="text-lg font-semibold">{finding.symbol}</div>
                <div className="text-sm text-muted-foreground">{finding.packageName ?? finding.findingType}</div>
              </div>
              <Badge variant={duplicateSeverityVariant(finding.severity)}>{finding.severity}</Badge>
              <Badge variant="neutral">{finding.findingType}</Badge>
            </div>
            <div className="grid gap-4 lg:grid-cols-2">
              <InfoBlock title="Archives containing it">{finding.artifacts.join("\n")}</InfoBlock>
              <InfoBlock title="Recommended action">{finding.recommendation}</InfoBlock>
            </div>
            <div className="rounded-3xl border border-amber-500/30 bg-amber-500/10 px-4 py-4 text-sm text-amber-900 dark:text-amber-100">
              <div className="mb-2 flex items-center gap-2 font-medium">
                <Layers3 className="h-4 w-4" /> Classpath shadowing warning
              </div>
              <div>{finding.shadowingWarning}</div>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

export function LicensesPanel({ licenses }: { licenses: LicenseFinding[] }) {
  const [categoryFilter, setCategoryFilter] = useState<string>("ALL");

  const counts = useMemo(() => ({
    permissive: licenses.filter((license) => license.category === "permissive").length,
    weak: licenses.filter((license) => license.category === "weak copyleft").length,
    strong: licenses.filter((license) => license.category === "strong copyleft").length,
    unknown: licenses.filter((license) => license.category === "unknown").length,
    multiple: licenses.filter((license) => license.category === "multiple").length,
  }), [licenses]);

  const filtered = useMemo(
    () => licenses.filter((license) => categoryFilter === "ALL" || license.category === categoryFilter),
    [categoryFilter, licenses],
  );

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <CardTitle>Licenses</CardTitle>
            <CardDescription>
              Best-effort license extraction from embedded Maven POM metadata, manifest fields, and license file heuristics. Strong copyleft and unknown licenses are highlighted for faster review.
            </CardDescription>
          </div>
          <select
            value={categoryFilter}
            onChange={(event) => setCategoryFilter(event.target.value)}
            className="rounded-full border border-border bg-background px-4 py-2 text-sm outline-none"
          >
            <option value="ALL">All categories</option>
            <option value="permissive">Permissive</option>
            <option value="weak copyleft">Weak copyleft</option>
            <option value="strong copyleft">Strong copyleft</option>
            <option value="unknown">Unknown</option>
            <option value="multiple">Multiple</option>
          </select>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
            {[
              ["Permissive", counts.permissive, "low"],
              ["Weak copyleft", counts.weak, "medium"],
              ["Strong copyleft", counts.strong, "critical"],
              ["Unknown", counts.unknown, "neutral"],
              ["Multiple", counts.multiple, "info"],
            ].map(([label, value, variant]) => (
              <div key={String(label)} className="rounded-3xl border border-border/70 bg-background/70 px-4 py-4">
                <Badge variant={variant as never}>{label}</Badge>
                <div className="mt-3 font-display text-3xl font-semibold tracking-tight">{value}</div>
              </div>
            ))}
          </div>

          <div className="overflow-hidden rounded-3xl border border-border/70">
            <table className="min-w-full divide-y divide-border/70 text-sm">
              <thead className="bg-secondary/60 text-left">
                <tr>
                  {["Dependency", "License", "Category", "Source", "Confidence", "Warnings"].map((header) => (
                    <th key={header} className="px-4 py-3 font-medium text-muted-foreground">{header}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60 bg-background/70">
                {filtered.map((license, index) => (
                  <tr key={`${license.groupId}:${license.artifactId}:${license.version}:${index}`}>
                    <td className="px-4 py-3 align-top">
                      <div className="font-medium">{license.groupId ?? "unknown"}:{license.artifactId ?? "unknown"}</div>
                      <div className="text-muted-foreground">{license.version ?? "unknown"}</div>
                    </td>
                    <td className="px-4 py-3 align-top">
                      <div>{license.licenseName}</div>
                      {license.licenseUrl ? <a className="text-primary hover:underline" href={license.licenseUrl} target="_blank" rel="noreferrer">{license.licenseUrl}</a> : null}
                    </td>
                    <td className="px-4 py-3 align-top"><Badge variant={licenseCategoryVariant(license.category)}>{license.category}</Badge></td>
                    <td className="px-4 py-3 align-top">{license.source}</td>
                    <td className="px-4 py-3 align-top">{license.confidence}</td>
                    <td className="px-4 py-3 align-top text-muted-foreground">
                      {license.warnings.length > 0 ? license.warnings.join(" ") : "None"}
                    </td>
                  </tr>
                ))}
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-5 text-muted-foreground">No license findings matched the selected category filter.</td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

export function UsageAnalysisPanel({ findings }: { findings: DependencyUsageFinding[] }) {
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [sortBy, setSortBy] = useState("status");

  const statuses = useMemo(
    () => Array.from(new Set(findings.map((finding) => finding.status))).sort(),
    [findings],
  );

  const filtered = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    const rows = findings.filter((finding) => {
      const matchesStatus = statusFilter === "ALL" || finding.status === statusFilter;
      const matchesQuery = !normalizedQuery
        || `${finding.groupId ?? ""}:${finding.artifactId ?? ""}:${finding.version ?? ""}`.toLowerCase().includes(normalizedQuery)
        || finding.evidence.some((item) => item.toLowerCase().includes(normalizedQuery));
      return matchesStatus && matchesQuery;
    });

    return [...rows].sort((left, right) => {
      switch (sortBy) {
        case "confidence":
          return confidenceRank(left.confidence) - confidenceRank(right.confidence)
            || usageKey(left).localeCompare(usageKey(right));
        case "size":
          return (right.sizeBytes ?? 0) - (left.sizeBytes ?? 0) || usageKey(left).localeCompare(usageKey(right));
        default:
          return statusRank(left.status) - statusRank(right.status) || usageKey(left).localeCompare(usageKey(right));
      }
    });
  }, [findings, query, sortBy, statusFilter]);

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader className="space-y-4">
          <div>
            <CardTitle>Usage Analysis</CardTitle>
            <CardDescription>
              Dependency usage findings are evidence-based, not absolute. JARScan combines Maven `dependency:analyze`, bytecode references, packaging metadata, and runtime heuristics to explain why a dependency looks used, apparently unused, or only possibly runtime-used.
            </CardDescription>
          </div>
          <div className="rounded-3xl border border-amber-500/30 bg-amber-500/10 px-4 py-4 text-sm text-amber-900 dark:text-amber-100">
            Java dependencies may be used through reflection, configuration, ServiceLoader, Spring auto-configuration, servlet containers, logging frameworks, JDBC drivers, or runtime plugin loading. Removal suggestions should be reviewed and tested.
          </div>
          <div className="flex flex-col gap-3 lg:flex-row">
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm outline-none"
              placeholder="Search dependency or evidence"
            />
            <select
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm outline-none"
            >
              <option value="ALL">All statuses</option>
              {statuses.map((status) => (
                <option key={status} value={status}>{status}</option>
              ))}
            </select>
            <select
              value={sortBy}
              onChange={(event) => setSortBy(event.target.value)}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm outline-none"
            >
              <option value="status">Sort by status</option>
              <option value="confidence">Sort by confidence</option>
              <option value="size">Sort by size</option>
            </select>
          </div>
        </CardHeader>
      </Card>

      <div className="overflow-hidden rounded-3xl border border-border/70">
        <table className="min-w-full divide-y divide-border/70 text-sm">
          <thead className="bg-secondary/60 text-left">
            <tr>
              {["Dependency", "Status", "Confidence", "Size", "Vulns", "Evidence", "Suggested action"].map((header) => (
                <th key={header} className="px-4 py-3 font-medium text-muted-foreground">{header}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-border/60 bg-background/70">
            {filtered.map((finding, index) => (
              <tr key={`${usageKey(finding)}-${index}`}>
                <td className="px-4 py-3 align-top">
                  <div className="font-medium">{finding.groupId ?? "unknown"}:{finding.artifactId ?? "unknown"}</div>
                  <div className="text-muted-foreground">{finding.version ?? "unknown"}</div>
                  {finding.paths.length > 0 ? (
                    <div className="mt-2 space-y-1 text-xs text-muted-foreground">
                      {finding.paths.slice(0, 2).map((path, pathIndex) => (
                        <div key={`${usageKey(finding)}-path-${pathIndex}`}>{path.join(" -> ")}</div>
                      ))}
                    </div>
                  ) : null}
                </td>
                <td className="px-4 py-3 align-top"><Badge variant={usageStatusVariant(finding.status)}>{finding.status}</Badge></td>
                <td className="px-4 py-3 align-top"><Badge variant={confidenceVariant(finding.confidence)}>{finding.confidence}</Badge></td>
                <td className="px-4 py-3 align-top">{formatBytesOrUnknown(finding.sizeBytes)}</td>
                <td className="px-4 py-3 align-top">{finding.vulnerabilitiesContributed ?? 0}</td>
                <td className="px-4 py-3 align-top text-muted-foreground">
                  <div className="space-y-2">
                    {finding.evidence.map((item) => <div key={item}>{item}</div>)}
                    {finding.warnings.map((warning) => (
                      <div key={warning} className="rounded-2xl border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-amber-900 dark:text-amber-100">
                        {warning}
                      </div>
                    ))}
                  </div>
                </td>
                <td className="px-4 py-3 align-top">{finding.suggestedAction}</td>
              </tr>
            ))}
            {filtered.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-5 text-muted-foreground">No dependency usage findings matched the current filters.</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function SlimmingAdvisorPanel({
  opportunities,
  awsBundleAdvice,
}: {
  opportunities: SlimmingOpportunity[];
  awsBundleAdvice: AwsBundleAdvice | null;
}) {
  if (opportunities.length === 0 && !awsBundleAdvice) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Slimming Advisor</CardTitle>
          <CardDescription>No obvious dependency slimming opportunities were detected from the current evidence set.</CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Slimming Advisor</CardTitle>
          <CardDescription>
            These opportunities combine usage evidence, size, vulnerability contribution, duplicate/provider overlap, version sprawl, and AWS bundle heuristics. Review and test before applying exclusions or removing dependencies.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="rounded-3xl border border-amber-500/30 bg-amber-500/10 px-4 py-4 text-sm text-amber-900 dark:text-amber-100">
            Java dependencies may be used through reflection, configuration, ServiceLoader, Spring auto-configuration, servlet containers, logging frameworks, JDBC drivers, or runtime plugin loading. Removal suggestions should be reviewed and tested.
          </div>
        </CardContent>
      </Card>

      {awsBundleAdvice ? (
        <Card className="border-sky-500/30">
          <CardContent className="space-y-4 p-6">
            <div className="flex items-center gap-3">
              <TrendingDown className="h-5 w-5 text-sky-600" />
              <div>
                <div className="text-lg font-semibold">AWS SDK Bundle Detected</div>
                <div className="text-sm text-muted-foreground">{awsBundleAdvice.detectedArtifact}</div>
              </div>
              <Badge variant={confidenceVariant(awsBundleAdvice.confidence)}>{awsBundleAdvice.confidence}</Badge>
            </div>
            <div className="grid gap-4 lg:grid-cols-2">
              <InfoBlock title="Used services">{awsBundleAdvice.usedServiceModules.length > 0 ? awsBundleAdvice.usedServiceModules.join(", ") : "None clearly detected"}</InfoBlock>
              <InfoBlock title="Apparently unused services">{awsBundleAdvice.apparentlyUnusedServiceModules.join(", ")}</InfoBlock>
            </div>
            <InfoBlock title="Suggested replacement">{awsBundleAdvice.suggestedReplacement ?? "Review bundle usage before narrowing modules."}</InfoBlock>
            {awsBundleAdvice.mavenSnippet ? <SnippetCard title="Suggested AWS replacement snippet" snippet={awsBundleAdvice.mavenSnippet} /> : null}
            <div className="space-y-2 text-sm text-muted-foreground">
              {awsBundleAdvice.warnings.map((warning) => <div key={warning}>{warning}</div>)}
            </div>
          </CardContent>
        </Card>
      ) : null}

      {opportunities.map((opportunity, index) => (
        <Card key={`${opportunity.title}-${index}`}>
          <CardContent className="space-y-4 p-6">
            <div className="flex flex-wrap items-center gap-3">
              <div>
                <div className="text-lg font-semibold">{opportunity.title}</div>
                <div className="text-sm text-muted-foreground">{opportunity.dependency}</div>
              </div>
              <Badge variant="neutral">{opportunity.opportunityType}</Badge>
              <Badge variant={confidenceVariant(opportunity.confidence)}>{opportunity.confidence}</Badge>
              {opportunity.usageStatus ? <Badge variant={usageStatusVariant(opportunity.usageStatus)}>{opportunity.usageStatus}</Badge> : null}
            </div>

            <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-4">
              <InfoBlock title="Estimated size impact">{formatBytesOrUnknown(opportunity.sizeImpactBytes)}</InfoBlock>
              <InfoBlock title="Transitive children">{String(opportunity.transitiveDependencyCount ?? 0)}</InfoBlock>
              <InfoBlock title="Vulnerability impact">{String(opportunity.vulnerabilitiesContributed ?? 0)}</InfoBlock>
              <InfoBlock title="Suggested replacement">{opportunity.suggestedReplacement ?? "None"}</InfoBlock>
            </div>

            <div className="space-y-2 text-sm text-muted-foreground">
              {opportunity.evidence.map((item) => <div key={item}>{item}</div>)}
            </div>

            {opportunity.mavenSnippet ? <SnippetCard title="Maven snippet" snippet={opportunity.mavenSnippet} /> : null}
            {opportunity.exclusionSnippet ? <SnippetCard title="Exclusion snippet" snippet={opportunity.exclusionSnippet} /> : null}

            <div className="space-y-2 text-sm text-muted-foreground">
              {opportunity.warnings.map((warning) => (
                <div key={warning} className="rounded-2xl border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-amber-900 dark:text-amber-100">
                  {warning}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

function SnippetCard({ title, snippet }: { title: string; snippet: string }) {
  return (
    <div className="space-y-2 rounded-3xl border border-border/70 bg-background/70 px-4 py-4">
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-2 text-sm font-medium">
          <Scale className="h-4 w-4" /> {title}
        </div>
        <Button type="button" variant="outline" size="sm" onClick={() => void navigator.clipboard?.writeText(snippet)}>
          <Copy className="mr-2 h-4 w-4" /> Copy
        </Button>
      </div>
      <pre className="overflow-x-auto rounded-2xl bg-slate-950 p-4 text-xs text-slate-200">{snippet}</pre>
    </div>
  );
}

function InfoBlock({ title, children }: { title: string; children: string }) {
  return (
    <div className="rounded-3xl border border-border/70 bg-background/70 px-4 py-4">
      <div className="mb-2 text-xs uppercase tracking-[0.2em] text-muted-foreground">{title}</div>
      <div className="whitespace-pre-line break-words text-sm">{children}</div>
    </div>
  );
}

function riskVariant(risk: string) {
  switch (risk) {
    case "HIGH":
      return "critical" as const;
    case "MEDIUM":
      return "medium" as const;
    default:
      return "neutral" as const;
  }
}

function duplicateSeverityVariant(severity: string) {
  switch (severity) {
    case "HIGH":
      return "critical" as const;
    case "MEDIUM":
      return "medium" as const;
    default:
      return "neutral" as const;
  }
}

function licenseCategoryVariant(category: string) {
  switch (category) {
    case "permissive":
      return "low" as const;
    case "weak copyleft":
      return "medium" as const;
    case "strong copyleft":
      return "critical" as const;
    case "multiple":
      return "info" as const;
    default:
      return "neutral" as const;
  }
}

function usageStatusVariant(status: string) {
  switch (status) {
    case "USED":
      return "low" as const;
    case "USED_UNDECLARED":
      return "high" as const;
    case "POSSIBLY_RUNTIME_USED":
      return "medium" as const;
    case "DECLARED_BUT_UNUSED":
    case "APPARENTLY_UNUSED":
    case "PACKAGED_BUT_APPARENTLY_UNUSED":
      return "critical" as const;
    default:
      return "neutral" as const;
  }
}

function confidenceVariant(confidence: string) {
  switch (confidence) {
    case "HIGH":
      return "critical" as const;
    case "MEDIUM":
      return "medium" as const;
    case "LOW":
      return "neutral" as const;
    default:
      return "neutral" as const;
  }
}

function usageKey(finding: DependencyUsageFinding) {
  return `${finding.groupId ?? "unknown"}:${finding.artifactId ?? "unknown"}:${finding.version ?? "unknown"}`;
}

function confidenceRank(confidence: string) {
  switch (confidence) {
    case "HIGH":
      return 0;
    case "MEDIUM":
      return 1;
    case "LOW":
      return 2;
    default:
      return 3;
  }
}

function statusRank(status: string) {
  switch (status) {
    case "USED_UNDECLARED":
      return 0;
    case "DECLARED_BUT_UNUSED":
    case "APPARENTLY_UNUSED":
    case "PACKAGED_BUT_APPARENTLY_UNUSED":
      return 1;
    case "POSSIBLY_RUNTIME_USED":
      return 2;
    case "USED":
      return 3;
    default:
      return 4;
  }
}

function formatBytesOrUnknown(value: number | null) {
  if (value == null) {
    return "n/a";
  }
  return `${(value / 1024 / 1024).toFixed(2)} MB`;
}
