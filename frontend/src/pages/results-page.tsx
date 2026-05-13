import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { toast } from "sonner";

import { ResultsDashboard } from "@/components/results-dashboard";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { fetchJobResult, fetchStoredScan, fetchStoredScanByJob } from "@/lib/api";
import type { AnalysisResult, StoredScanSummary } from "@/lib/types";

function ResultsSkeleton() {
  return (
    <div className="space-y-8">
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {Array.from({ length: 5 }).map((_, index) => (
          <Card key={index}>
            <CardContent className="p-5">
              <div className="h-4 w-24 animate-pulse rounded-full bg-secondary" />
              <div className="mt-3 h-10 w-28 animate-pulse rounded-2xl bg-secondary" />
            </CardContent>
          </Card>
        ))}
      </div>
      <Card>
        <CardContent className="space-y-4 p-6">
          <div className="h-6 w-40 animate-pulse rounded-full bg-secondary" />
          <div className="h-12 w-full animate-pulse rounded-3xl bg-secondary" />
          <div className="h-12 w-full animate-pulse rounded-3xl bg-secondary" />
          <div className="h-12 w-full animate-pulse rounded-3xl bg-secondary" />
        </CardContent>
      </Card>
    </div>
  );
}

export function ResultsPage() {
  const { jobId, scanId } = useParams();
  const [result, setResult] = useState<AnalysisResult | null>(null);
  const [scanSummary, setScanSummary] = useState<StoredScanSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!jobId && !scanId) return;
    let cancelled = false;

    const load = async () => {
      try {
        setIsLoading(true);
        if (scanId) {
          const stored = await fetchStoredScan(scanId);
          if (cancelled) return;
          setScanSummary(stored.summary);
          setResult(stored.result);
          return;
        }

        if (jobId) {
          try {
            const stored = await fetchStoredScanByJob(jobId);
            if (cancelled) return;
            setScanSummary(stored.summary);
            setResult(stored.result);
            return;
          } catch {
            const nextResult = await fetchJobResult(jobId);
            if (cancelled) return;
            setScanSummary(null);
            setResult(nextResult);
          }
        }
      } catch (error) {
        if (!cancelled) {
          toast.error(error instanceof Error ? error.message : "Unable to load results");
          setResult(null);
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
  }, [jobId, scanId]);

  if (isLoading) {
    return <ResultsSkeleton />;
  }

  if (!result) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Result unavailable</CardTitle>
          <CardDescription>
            {scanSummary
              ? "This stored scan does not include a completed result payload."
              : "The analysis result could not be loaded."}
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  const exportJobId = scanSummary?.jobId ?? jobId ?? result.jobId;
  const sourceLabel = scanSummary
    ? `Reopened from persisted scan history for ${scanSummary.inputName ?? scanSummary.jobId}`
    : undefined;

  return (
    <ResultsDashboard
      result={result}
      exportJobId={exportJobId}
      sourceLabel={sourceLabel}
      scanId={scanSummary?.scanId}
      onRefresh={async () => {
        if (scanSummary?.scanId) {
          const stored = await fetchStoredScan(scanSummary.scanId);
          setScanSummary(stored.summary);
          setResult(stored.result);
          return;
        }
        if (jobId) {
          const stored = await fetchStoredScanByJob(jobId);
          setScanSummary(stored.summary);
          setResult(stored.result);
        }
      }}
    />
  );
}
