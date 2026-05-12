import { ArrowRightLeft } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { fetchScanComparison, fetchScans } from "@/lib/api";
import type { CountDiff, ScanComparisonResponse, StoredScanSummary } from "@/lib/types";

function deltaTone(delta: number, lowerIsBetter: boolean) {
  if (delta === 0) return { label: "unchanged", variant: "neutral" as const };
  const improved = lowerIsBetter ? delta < 0 : delta > 0;
  return improved
    ? { label: "improved", variant: "low" as const }
    : { label: "worsened", variant: "critical" as const };
}

function MetricRow({
  label,
  diff,
  lowerIsBetter,
}: {
  label: string;
  diff: CountDiff;
  lowerIsBetter: boolean;
}) {
  const tone = deltaTone(diff.delta, lowerIsBetter);
  return (
    <div className="rounded-2xl border border-border/70 bg-background/60 px-4 py-3">
      <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{label}</div>
      <div className="mt-2 flex items-center gap-2 text-lg font-semibold">
        <span>{diff.before}</span>
        <ArrowRightLeft className="h-4 w-4 text-muted-foreground" />
        <span>{diff.after}</span>
      </div>
      <Badge variant={tone.variant} className="mt-2">{tone.label}</Badge>
    </div>
  );
}

export function ComparePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [scans, setScans] = useState<StoredScanSummary[]>([]);
  const [baseScanId, setBaseScanId] = useState(searchParams.get("base") ?? "");
  const [targetScanId, setTargetScanId] = useState(searchParams.get("target") ?? "");
  const [comparison, setComparison] = useState<ScanComparisonResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingScans, setIsLoadingScans] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const history = await fetchScans({ limit: 250, sort: "completedAt", direction: "desc" });
        if (!cancelled) {
          setScans(history.filter((scan) => scan.status === "COMPLETED"));
        }
      } catch (nextError) {
        if (!cancelled) {
          setError(nextError instanceof Error ? nextError.message : "Unable to load scans");
        }
      } finally {
        if (!cancelled) setIsLoadingScans(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const base = searchParams.get("base") ?? "";
    const target = searchParams.get("target") ?? "";
    setBaseScanId(base);
    setTargetScanId(target);
    if (!base || !target) return;
    let cancelled = false;
    const load = async () => {
      try {
        setIsLoading(true);
        setError(null);
        const result = await fetchScanComparison(base, target);
        if (!cancelled) {
          setComparison(result);
        }
      } catch (nextError) {
        if (!cancelled) {
          setError(nextError instanceof Error ? nextError.message : "Unable to compare scans");
          setComparison(null);
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [searchParams]);

  const canCompare = useMemo(() => !!baseScanId && !!targetScanId && baseScanId !== targetScanId, [baseScanId, targetScanId]);

  function runComparison() {
    if (!canCompare) return;
    setSearchParams({ base: baseScanId, target: targetScanId });
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Compare scans</CardTitle>
          <CardDescription>Select baseline and target scans to view dependency and vulnerability deltas.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-3">
          <select value={baseScanId} onChange={(event) => setBaseScanId(event.target.value)} className="rounded-xl border p-3">
            <option value="">Select baseline scan</option>
            {scans.map((scan) => (
              <option key={scan.scanId} value={scan.scanId}>
                {scan.inputName ?? scan.jobId} ({scan.completedAt ?? scan.createdAt})
              </option>
            ))}
          </select>
          <select value={targetScanId} onChange={(event) => setTargetScanId(event.target.value)} className="rounded-xl border p-3">
            <option value="">Select target scan</option>
            {scans.map((scan) => (
              <option key={scan.scanId} value={scan.scanId}>
                {scan.inputName ?? scan.jobId} ({scan.completedAt ?? scan.createdAt})
              </option>
            ))}
          </select>
          <Button disabled={!canCompare || isLoadingScans || isLoading} onClick={runComparison}>
            {isLoading ? "Comparing..." : "Compare"}
          </Button>
        </CardContent>
      </Card>

      {error ? (
        <Card>
          <CardHeader>
            <CardTitle>Comparison error</CardTitle>
            <CardDescription>{error}</CardDescription>
          </CardHeader>
        </Card>
      ) : null}

      {isLoading ? <Card><CardHeader><CardTitle>Loading comparison...</CardTitle></CardHeader></Card> : null}

      {!isLoading && comparison ? (
        <>
          <Card>
            <CardHeader>
              <CardTitle>Summary changes</CardTitle>
              <CardDescription>
                Baseline: {comparison.baseline.inputName ?? comparison.baseline.jobId} to target: {comparison.target.inputName ?? comparison.target.jobId}
              </CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
              <MetricRow label="Vulnerabilities" diff={comparison.summaryDiff.totalVulnerabilities} lowerIsBetter />
              <MetricRow label="Critical" diff={comparison.summaryDiff.critical} lowerIsBetter />
              <MetricRow label="High" diff={comparison.summaryDiff.high} lowerIsBetter />
              <MetricRow label="Artifacts" diff={comparison.summaryDiff.totalArtifacts} lowerIsBetter={false} />
              <MetricRow label="Dependencies" diff={comparison.summaryDiff.totalDependencies} lowerIsBetter={false} />
              <div className="rounded-2xl border border-border/70 bg-background/60 px-4 py-3">
                <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Highest CVSS</div>
                <div className="mt-2 text-lg font-semibold">
                  {(comparison.summaryDiff.highestCvss.before ?? 0).toFixed(1)} <ArrowRightLeft className="mx-2 inline h-4 w-4 text-muted-foreground" /> {(comparison.summaryDiff.highestCvss.after ?? 0).toFixed(1)}
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Dependency changes</CardTitle>
              <CardDescription>
                Added {comparison.dependencyChanges.addedCount} • Removed {comparison.dependencyChanges.removedCount} • Updated {comparison.dependencyChanges.updatedCount}
              </CardDescription>
            </CardHeader>
            <CardContent className="overflow-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-muted-foreground">
                    <th className="p-2">Change</th><th className="p-2">groupId</th><th className="p-2">artifactId</th><th className="p-2">Old version</th><th className="p-2">New version</th><th className="p-2">Scope</th><th className="p-2">Vulns</th>
                  </tr>
                </thead>
                <tbody>
                  {comparison.dependencyChanges.changes.map((change) => (
                    <tr key={change.artifactKey} className="border-t">
                      <td className="p-2"><Badge variant="neutral">{change.changeType.toLowerCase()}</Badge></td>
                      <td className="p-2">{change.newGroupId ?? change.oldGroupId ?? "n/a"}</td>
                      <td className="p-2">{change.newArtifactId ?? change.oldArtifactId ?? "n/a"}</td>
                      <td className="p-2">{change.oldVersion ?? "n/a"}</td>
                      <td className="p-2">{change.newVersion ?? "n/a"}</td>
                      <td className="p-2">{change.scope ?? "n/a"}</td>
                      <td className="p-2">{change.oldVulnerabilityCount ?? 0} → {change.newVulnerabilityCount ?? 0}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Vulnerability changes</CardTitle>
              <CardDescription>
                New {comparison.vulnerabilityChanges.newCount} • Fixed {comparison.vulnerabilityChanges.fixedCount} • Changed {comparison.vulnerabilityChanges.changedCount}
              </CardDescription>
            </CardHeader>
            <CardContent className="overflow-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-muted-foreground">
                    <th className="p-2">Change</th><th className="p-2">CVE</th><th className="p-2">Severity</th><th className="p-2">CVSS</th><th className="p-2">Dependency</th><th className="p-2">Old version</th><th className="p-2">New version</th>
                  </tr>
                </thead>
                <tbody>
                  {comparison.vulnerabilityChanges.changes.map((change) => (
                    <tr key={change.vulnerabilityId} className="border-t">
                      <td className="p-2"><Badge variant="neutral">{change.changeType.toLowerCase()}</Badge></td>
                      <td className="p-2">{change.cveId ?? "n/a"}</td>
                      <td className="p-2">{change.newSeverity ?? change.oldSeverity ?? "n/a"}</td>
                      <td className="p-2">{change.newCvss ?? change.oldCvss ?? "n/a"}</td>
                      <td className="p-2">{change.dependencyGroupId ?? "?"}:{change.dependencyArtifactId ?? "?"}</td>
                      <td className="p-2">{change.oldDependencyVersion ?? "n/a"}</td>
                      <td className="p-2">{change.newDependencyVersion ?? "n/a"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          </Card>

          {comparison.warnings.length > 0 ? (
            <Card>
              <CardHeader><CardTitle>Warnings</CardTitle></CardHeader>
              <CardContent className="space-y-2">{comparison.warnings.map((warning) => <p key={warning} className="text-sm text-muted-foreground">{warning}</p>)}</CardContent>
            </Card>
          ) : null}
        </>
      ) : null}

      {!comparison && !isLoading && !error ? (
        <Card>
          <CardHeader>
            <CardTitle>No comparison loaded</CardTitle>
            <CardDescription>Pick two completed scans and run comparison, or start from <Link className="underline" to="/scan-history">scan history</Link>.</CardDescription>
          </CardHeader>
        </Card>
      ) : null}
    </div>
  );
}
