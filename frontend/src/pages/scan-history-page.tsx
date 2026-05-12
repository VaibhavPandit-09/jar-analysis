import { motion } from "framer-motion";
import {
  Download,
  ExternalLink,
  FileClock,
  Filter,
  PencilLine,
  Search,
  Trash2,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  buildJobExportUrl,
  deleteStoredScan,
  fetchScans,
  updateStoredScan,
} from "@/lib/api";
import type { InputType, JobStatus, Severity, StoredScanSummary } from "@/lib/types";
import { cn } from "@/lib/utils";

type SortOption =
  | "date-desc"
  | "date-asc"
  | "vulns-desc"
  | "critical-desc"
  | "high-desc"
  | "duration-desc";

const severityVariant: Record<Severity, "critical" | "high" | "medium" | "low" | "info" | "neutral"> = {
  CRITICAL: "critical",
  HIGH: "high",
  MEDIUM: "medium",
  LOW: "low",
  INFO: "info",
  UNKNOWN: "neutral",
};

const statusVariant: Record<JobStatus, "critical" | "high" | "medium" | "low" | "info" | "neutral"> = {
  FAILED: "critical",
  CANCELLED: "medium",
  QUEUED: "neutral",
  RUNNING: "info",
  COMPLETED: "low",
};

function formatTimestamp(value: string | null) {
  if (!value) return "Unknown";
  return new Date(value).toLocaleString([], {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatDuration(durationMs: number | null) {
  if (!durationMs || durationMs < 1000) return durationMs === 0 ? "0s" : "n/a";
  const totalSeconds = Math.round(durationMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes === 0) return `${seconds}s`;
  if (minutes < 60) return `${minutes}m ${seconds}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
}

function humanInputType(inputType: InputType) {
  return inputType === "POM" ? "POM" : "Archives";
}

function sortScans(scans: StoredScanSummary[], sort: SortOption) {
  return [...scans].sort((left, right) => {
    switch (sort) {
      case "date-asc":
        return new Date(left.completedAt ?? left.createdAt).getTime() - new Date(right.completedAt ?? right.createdAt).getTime();
      case "vulns-desc":
        return right.totalVulnerabilities - left.totalVulnerabilities;
      case "critical-desc":
        return right.criticalCount - left.criticalCount;
      case "high-desc":
        return right.highCount - left.highCount;
      case "duration-desc":
        return (right.durationMs ?? -1) - (left.durationMs ?? -1);
      case "date-desc":
      default:
        return new Date(right.completedAt ?? right.createdAt).getTime() - new Date(left.completedAt ?? left.createdAt).getTime();
    }
  });
}

function HistorySkeleton() {
  return (
    <div className="space-y-4">
      {Array.from({ length: 4 }).map((_, index) => (
        <Card key={index}>
          <CardContent className="space-y-4 p-6">
            <div className="h-5 w-48 animate-pulse rounded-full bg-secondary" />
            <div className="grid gap-3 md:grid-cols-4">
              {Array.from({ length: 4 }).map((__, itemIndex) => (
                <div key={itemIndex} className="h-16 animate-pulse rounded-2xl bg-secondary" />
              ))}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

export function ScanHistoryPage() {
  const [scans, setScans] = useState<StoredScanSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [inputType, setInputType] = useState<"ALL" | InputType>("ALL");
  const [status, setStatus] = useState<"ALL" | JobStatus>("ALL");
  const [severity, setSeverity] = useState<"ALL" | Severity>("ALL");
  const [sort, setSort] = useState<SortOption>("date-desc");
  const [editingScanId, setEditingScanId] = useState<string | null>(null);
  const [draftNotes, setDraftNotes] = useState("");
  const [draftTags, setDraftTags] = useState("");
  const [savingScanId, setSavingScanId] = useState<string | null>(null);
  const [scanToDelete, setScanToDelete] = useState<StoredScanSummary | null>(null);
  const [deletingScanId, setDeletingScanId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        setIsLoading(true);
        setError(null);
        const history = await fetchScans({ limit: 250, sort: "completedAt", direction: "desc" });
        if (!cancelled) {
          setScans(history);
        }
      } catch (nextError) {
        if (!cancelled) {
          setError(nextError instanceof Error ? nextError.message : "Unable to load scan history");
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  const filteredScans = useMemo(() => {
    const loweredQuery = query.trim().toLowerCase();
    const next = scans.filter((scan) => {
      const matchesQuery =
        !loweredQuery ||
        (scan.inputName ?? "").toLowerCase().includes(loweredQuery) ||
        scan.jobId.toLowerCase().includes(loweredQuery) ||
        (scan.notes ?? "").toLowerCase().includes(loweredQuery) ||
        scan.tags.some((tag) => tag.toLowerCase().includes(loweredQuery));
      const matchesInputType = inputType === "ALL" || scan.inputType === inputType;
      const matchesStatus = status === "ALL" || scan.status === status;
      const matchesSeverity = severity === "ALL" || scan.highestSeverity === severity;
      return matchesQuery && matchesInputType && matchesStatus && matchesSeverity;
    });
    return sortScans(next, sort);
  }, [inputType, query, scans, severity, sort, status]);

  function beginEdit(scan: StoredScanSummary) {
    setEditingScanId(scan.scanId);
    setDraftNotes(scan.notes ?? "");
    setDraftTags(scan.tags.join(", "));
  }

  async function saveMetadata(scanId: string) {
    try {
      setSavingScanId(scanId);
      const updated = await updateStoredScan(scanId, {
        notes: draftNotes.trim() || null,
        tags: draftTags
          .split(",")
          .map((tag) => tag.trim())
          .filter(Boolean),
      });
      setScans((current) => current.map((scan) => (scan.scanId === scanId ? updated : scan)));
      setEditingScanId(null);
      toast.success("Scan notes updated");
    } catch (nextError) {
      toast.error(nextError instanceof Error ? nextError.message : "Unable to update scan");
    } finally {
      setSavingScanId(null);
    }
  }

  async function confirmDelete() {
    if (!scanToDelete) return;
    try {
      setDeletingScanId(scanToDelete.scanId);
      await deleteStoredScan(scanToDelete.scanId);
      setScans((current) => current.filter((scan) => scan.scanId !== scanToDelete.scanId));
      toast.success("Scan deleted");
      setScanToDelete(null);
    } catch (nextError) {
      toast.error(nextError instanceof Error ? nextError.message : "Unable to delete scan");
    } finally {
      setDeletingScanId(null);
    }
  }

  return (
    <>
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
        <Card className="overflow-hidden border-white/60 bg-gradient-to-br from-card to-card/80">
          <CardHeader className="gap-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
              <div>
                <CardTitle>Scan history</CardTitle>
                <CardDescription>
                  Reopen persisted results, export prior scans, and keep lightweight notes on important findings.
                </CardDescription>
              </div>
              <div className="flex items-center gap-2 text-xs uppercase tracking-[0.24em] text-muted-foreground">
                <FileClock className="h-3.5 w-3.5" />
                Stored in Docker-backed SQLite history
              </div>
            </div>

            <div className="grid gap-3 lg:grid-cols-[1.3fr_repeat(4,minmax(0,1fr))]">
              <div className="flex items-center gap-2 rounded-full border border-border bg-background px-4 py-3">
                <Search className="h-4 w-4 text-muted-foreground" />
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Search by input, note, tag, or job ID"
                  className="w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
                />
              </div>

              <select
                aria-label="Filter by input type"
                value={inputType}
                onChange={(event) => setInputType(event.target.value as "ALL" | InputType)}
                className="rounded-full border border-border bg-background px-4 py-3 text-sm outline-none"
              >
                <option value="ALL">All input types</option>
                <option value="ARCHIVE_UPLOAD">Archives</option>
                <option value="POM">POM</option>
              </select>

              <select
                aria-label="Filter by status"
                value={status}
                onChange={(event) => setStatus(event.target.value as "ALL" | JobStatus)}
                className="rounded-full border border-border bg-background px-4 py-3 text-sm outline-none"
              >
                <option value="ALL">All statuses</option>
                {["COMPLETED", "FAILED", "CANCELLED", "RUNNING", "QUEUED"].map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>

              <select
                aria-label="Filter by severity"
                value={severity}
                onChange={(event) => setSeverity(event.target.value as "ALL" | Severity)}
                className="rounded-full border border-border bg-background px-4 py-3 text-sm outline-none"
              >
                <option value="ALL">All severities</option>
                {["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO", "UNKNOWN"].map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>

              <select
                aria-label="Sort scans"
                value={sort}
                onChange={(event) => setSort(event.target.value as SortOption)}
                className="rounded-full border border-border bg-background px-4 py-3 text-sm outline-none"
              >
                <option value="date-desc">Newest first</option>
                <option value="date-asc">Oldest first</option>
                <option value="vulns-desc">Most vulnerabilities</option>
                <option value="critical-desc">Most critical findings</option>
                <option value="high-desc">Most high findings</option>
                <option value="duration-desc">Longest duration</option>
              </select>
            </div>

            <div className="flex flex-wrap gap-2">
              <Badge variant="neutral" className="gap-2">
                <Filter className="h-3.5 w-3.5" />
                {filteredScans.length} of {scans.length} scans
              </Badge>
              <Badge variant="neutral">Sort: {sort.replace("-", " ")}</Badge>
            </div>
          </CardHeader>
        </Card>

        {isLoading ? <HistorySkeleton /> : null}

        {!isLoading && error ? (
          <Card>
            <CardHeader>
              <CardTitle>Unable to load scan history</CardTitle>
              <CardDescription>{error}</CardDescription>
            </CardHeader>
            <CardContent>
              <Button onClick={() => window.location.reload()}>Retry</Button>
            </CardContent>
          </Card>
        ) : null}

        {!isLoading && !error && filteredScans.length === 0 ? (
          <Card>
            <CardHeader>
              <CardTitle>No scans found</CardTitle>
              <CardDescription>
                {scans.length === 0
                  ? "Run an analysis and completed scans will appear here."
                  : "Adjust your search or filters to see more persisted scans."}
              </CardDescription>
            </CardHeader>
          </Card>
        ) : null}

        {!isLoading && !error ? (
          <div className="space-y-4">
            {filteredScans.map((scan) => {
              const canOpen = scan.status === "COMPLETED";
              const exportJobId = scan.jobId;
              const isEditing = editingScanId === scan.scanId;

              return (
                <Card key={scan.scanId}>
                  <CardContent className="space-y-5 p-6">
                    <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                      <div className="space-y-3">
                        <div className="flex flex-wrap items-center gap-3">
                          <h3 className="font-display text-2xl font-semibold tracking-tight">
                            {scan.inputName ?? scan.jobId}
                          </h3>
                          <Badge variant={statusVariant[scan.status]}>{scan.status}</Badge>
                          <Badge variant="neutral">{humanInputType(scan.inputType)}</Badge>
                          <Badge variant={severityVariant[scan.highestSeverity]}>{scan.highestSeverity}</Badge>
                        </div>
                        <div className="flex flex-wrap gap-2 text-sm text-muted-foreground">
                          <span>Completed {formatTimestamp(scan.completedAt ?? scan.createdAt)}</span>
                          <span>•</span>
                          <span>Duration {formatDuration(scan.durationMs)}</span>
                          <span>•</span>
                          <span>Job {scan.jobId}</span>
                        </div>
                        {scan.tags.length ? (
                          <div className="flex flex-wrap gap-2">
                            {scan.tags.map((tag) => (
                              <Badge key={tag} variant="info">
                                {tag}
                              </Badge>
                            ))}
                          </div>
                        ) : null}
                        {scan.notes ? (
                          <div className="max-w-3xl rounded-2xl border border-border/70 bg-secondary/30 px-4 py-3 text-sm text-muted-foreground">
                            {scan.notes}
                          </div>
                        ) : null}
                      </div>

                      <div className="flex flex-wrap gap-2">
                        <Link
                          to={`/scans/${scan.scanId}/results`}
                          className={cn(buttonVariants({ variant: "default" }), !canOpen && "pointer-events-none opacity-50")}
                          aria-disabled={!canOpen}
                        >
                          <ExternalLink className="mr-2 h-4 w-4" />
                          Open scan
                        </Link>
                        <Button variant="outline" onClick={() => beginEdit(scan)}>
                          <PencilLine className="mr-2 h-4 w-4" />
                          Edit notes
                        </Button>
                        <Button variant="outline" onClick={() => setScanToDelete(scan)}>
                          <Trash2 className="mr-2 h-4 w-4" />
                          Delete
                        </Button>
                      </div>
                    </div>

                    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
                      {[
                        ["Artifacts", scan.totalArtifacts],
                        ["Dependencies", scan.totalDependencies],
                        ["Vulnerabilities", scan.totalVulnerabilities],
                        ["Critical / High", `${scan.criticalCount} / ${scan.highCount}`],
                        ["Highest CVSS", scan.highestCvss?.toFixed(1) ?? "n/a"],
                        ["Java", scan.requiredJavaVersion ?? "Unknown"],
                      ].map(([label, value]) => (
                        <div key={String(label)} className="rounded-2xl border border-border/70 bg-background/60 px-4 py-4">
                          <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{label}</div>
                          <div className="mt-2 text-lg font-semibold">{value}</div>
                        </div>
                      ))}
                    </div>

                    <div className="flex flex-wrap gap-2">
                      {(["json", "markdown", "html"] as const).map((format) => (
                        <a
                          key={format}
                          href={buildJobExportUrl(exportJobId, format)}
                          download={`jarscan-${scan.jobId}.${format === "markdown" ? "md" : format}`}
                          className={cn(buttonVariants({ variant: "outline", size: "sm" }), !canOpen && "pointer-events-none opacity-50")}
                          aria-disabled={!canOpen}
                        >
                          {format === "json" ? <Download className="mr-2 h-4 w-4" /> : null}
                          Export {format === "markdown" ? "Markdown" : format.toUpperCase()}
                        </a>
                      ))}
                    </div>

                    {isEditing ? (
                      <div className="grid gap-4 rounded-[28px] border border-border/70 bg-background/60 p-5 lg:grid-cols-[1.1fr_0.9fr]">
                        <div className="space-y-3">
                          <label className="block text-sm font-medium">Notes</label>
                          <textarea
                            value={draftNotes}
                            onChange={(event) => setDraftNotes(event.target.value)}
                            rows={5}
                            className="w-full rounded-3xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary"
                            placeholder="Capture context for this scan"
                          />
                        </div>
                        <div className="space-y-3">
                          <label className="block text-sm font-medium">Tags</label>
                          <input
                            value={draftTags}
                            onChange={(event) => setDraftTags(event.target.value)}
                            className="w-full rounded-full border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary"
                            placeholder="critical-path, release, spring-boot"
                          />
                          <p className="text-sm text-muted-foreground">
                            Separate tags with commas. These fields are stored in the local SQLite history.
                          </p>
                          <div className="flex flex-wrap gap-3">
                            <Button onClick={() => void saveMetadata(scan.scanId)} disabled={savingScanId === scan.scanId}>
                              {savingScanId === scan.scanId ? "Saving..." : "Save metadata"}
                            </Button>
                            <Button variant="outline" onClick={() => setEditingScanId(null)}>
                              Cancel
                            </Button>
                          </div>
                        </div>
                      </div>
                    ) : null}
                  </CardContent>
                </Card>
              );
            })}
          </div>
        ) : null}
      </motion.div>

      <ConfirmDialog
        open={scanToDelete !== null}
        title="Delete stored scan?"
        description={
          scanToDelete
            ? `Delete ${scanToDelete.inputName ?? scanToDelete.jobId} from persisted history. This removes the saved SQLite record but does not affect Docker caches or any original uploaded files.`
            : ""
        }
        confirmLabel="Delete scan"
        confirming={deletingScanId !== null}
        onCancel={() => setScanToDelete(null)}
        onConfirm={() => void confirmDelete()}
      />
    </>
  );
}
