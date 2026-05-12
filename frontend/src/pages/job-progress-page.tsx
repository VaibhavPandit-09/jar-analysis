import { AnimatePresence, motion } from "framer-motion";
import { AlertTriangle, ArrowRight, CircleDot, Square } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { cancelJob, fetchJobStatus } from "@/lib/api";
import type { AnalysisJobStatus, ProgressEvent } from "@/lib/types";
import { cn } from "@/lib/utils";

const TERMINAL = new Set(["COMPLETED", "FAILED", "CANCELLED"]);

export function JobProgressPage() {
  const { jobId = "" } = useParams();
  const [status, setStatus] = useState<AnalysisJobStatus | null>(null);
  const [events, setEvents] = useState<ProgressEvent[]>([]);
  const [isCancelling, setIsCancelling] = useState(false);

  useEffect(() => {
    if (!jobId) return;
    let cancelled = false;

    const load = async () => {
      try {
        const nextStatus = await fetchJobStatus(jobId);
        if (!cancelled) {
          setStatus(nextStatus);
        }
      } catch (error) {
        if (!cancelled) {
          toast.error(error instanceof Error ? error.message : "Unable to load job status");
        }
      }
    };

    void load();
    const interval = window.setInterval(() => {
      void load();
    }, 2000);

    const stream = new EventSource(`/api/jobs/${jobId}/events`);
    const onEvent = (event: MessageEvent<string>) => {
      const parsed = JSON.parse(event.data) as ProgressEvent;
      setEvents((current) => [...current.slice(-199), parsed]);
    };

    ["STARTED", "PROGRESS", "LOG", "WARNING", "ERROR", "COMPLETED", "CANCELLED"].forEach((type) =>
      stream.addEventListener(type, onEvent),
    );

    stream.onerror = () => {
      if (!cancelled) {
        stream.close();
      }
    };

    return () => {
      cancelled = true;
      window.clearInterval(interval);
      stream.close();
    };
  }, [jobId]);

  const percent = useMemo(() => {
    for (let index = events.length - 1; index >= 0; index -= 1) {
      if (typeof events[index].percent === "number") return events[index].percent;
    }
    return status?.status === "COMPLETED" ? 100 : 8;
  }, [events, status]);

  const latest = events.at(-1);
  const timeline = useMemo(
    () =>
      Array.from(
        new Map(
          events
            .filter((event) => event.type !== "LOG")
            .map((event) => [event.phase, event]),
        ).values(),
      ),
    [events],
  );
  const logs = useMemo(() => events.filter((event) => ["LOG", "WARNING", "ERROR"].includes(event.type)), [events]);

  async function handleCancel() {
    try {
      setIsCancelling(true);
      const nextStatus = await cancelJob(jobId);
      setStatus(nextStatus);
      toast.success("Cancellation requested");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to cancel job");
    } finally {
      setIsCancelling(false);
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[0.95fr_1.05fr]">
      <Card>
        <CardHeader>
          <Badge variant={status?.status === "FAILED" ? "critical" : "info"} className="w-fit">
            {status?.status ?? "RUNNING"}
          </Badge>
          <CardTitle>Analysis progress</CardTitle>
          <CardDescription>
            {latest?.message ?? status?.message ?? "Waiting for the first progress event"}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <Progress value={percent} className="h-4" />
          <div className="flex items-center justify-between text-sm text-muted-foreground">
            <span>{percent}% complete</span>
            <span>{latest?.currentItem ?? "Preparing workload"}</span>
          </div>

          <div className="space-y-3">
            {timeline.map((event) => (
              <div key={`${event.phase}-${event.timestamp}`} className="flex items-start gap-3 rounded-2xl border border-border/70 bg-background/60 px-4 py-4">
                <CircleDot className="mt-0.5 h-4 w-4 text-primary" />
                <div className="min-w-0">
                  <div className="text-sm font-medium">{event.phase.replaceAll("_", " ")}</div>
                  <div className="mt-1 text-sm text-muted-foreground">{event.message}</div>
                </div>
              </div>
            ))}
          </div>

          <div className="flex flex-wrap gap-3">
            {!TERMINAL.has(status?.status ?? "") ? (
              <Button variant="destructive" onClick={handleCancel} disabled={isCancelling}>
                <Square className="mr-2 h-4 w-4" />
                {isCancelling ? "Cancelling..." : "Cancel job"}
              </Button>
            ) : null}
            {status?.status === "COMPLETED" ? (
              <Link to={`/jobs/${jobId}/results`} className={cn(buttonVariants({ variant: "default" }))}>
                View results
                <ArrowRight className="ml-2 h-4 w-4" />
              </Link>
            ) : null}
          </div>
        </CardContent>
      </Card>

      <Card className="overflow-hidden">
        <CardHeader>
          <CardTitle>Live log stream</CardTitle>
          <CardDescription>Maven resolution and scanner output stream into this panel in real time.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="h-[520px] overflow-y-auto rounded-[26px] border border-border/70 bg-slate-950 p-4 font-mono text-xs text-slate-200">
            <AnimatePresence initial={false}>
              {logs.length === 0 ? (
                <div className="flex h-full items-center justify-center text-center text-slate-400">
                  Log output will appear here once the job enters a verbose phase.
                </div>
              ) : null}
              {logs.map((event) => (
                <motion.div
                  key={`${event.timestamp}-${event.message}`}
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="mb-2 flex gap-3 border-b border-white/5 pb-2 last:mb-0 last:border-0 last:pb-0"
                >
                  <span className="text-slate-500">
                    {new Date(event.timestamp).toLocaleTimeString([], {
                      hour: "2-digit",
                      minute: "2-digit",
                      second: "2-digit",
                    })}
                  </span>
                  <span className="text-slate-100">{event.message}</span>
                </motion.div>
              ))}
            </AnimatePresence>
          </div>

          {status?.errors.length ? (
            <div className="mt-4 rounded-2xl border border-rose-500/25 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
              <div className="mb-2 flex items-center gap-2 font-medium text-rose-100">
                <AlertTriangle className="h-4 w-4" />
                Failure details
              </div>
              {status.errors.join("\n")}
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
