import { AnimatePresence, motion } from "framer-motion";
import { DatabaseZap, KeyRound, RefreshCw, ShieldCheck } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  deleteNvdSettings,
  fetchNvdSettings,
  fetchVulnerabilityDbStatus,
  saveNvdSettings,
  syncVulnerabilityDb,
  testNvdSettings,
} from "@/lib/api";
import type { NvdSettingsStatus, ProgressEvent, VulnerabilityDbStatus } from "@/lib/types";

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
  if (!durationMs) return "n/a";
  const totalSeconds = Math.round(durationMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes === 0) return `${seconds}s`;
  return `${minutes}m ${seconds}s`;
}

export function SettingsPage() {
  const [nvdSettings, setNvdSettings] = useState<NvdSettingsStatus | null>(null);
  const [dbStatus, setDbStatus] = useState<VulnerabilityDbStatus | null>(null);
  const [apiKey, setApiKey] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [isTesting, setIsTesting] = useState(false);
  const [isRemoving, setIsRemoving] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);
  const [syncEvents, setSyncEvents] = useState<ProgressEvent[]>([]);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const [settings, status] = await Promise.all([
          fetchNvdSettings(),
          fetchVulnerabilityDbStatus(),
        ]);
        if (!cancelled) {
          setNvdSettings(settings);
          setDbStatus(status);
        }
      } catch (error) {
        if (!cancelled) {
          toast.error(error instanceof Error ? error.message : "Unable to load settings");
        }
      }
    };

    void load();
    const interval = window.setInterval(load, 30000);

    const stream = new EventSource("/api/vulnerability-db/events");
    const onEvent = (event: MessageEvent<string>) => {
      const parsed = JSON.parse(event.data) as ProgressEvent;
      setSyncEvents((current) => [...current.slice(-79), parsed]);
      if (parsed.type === "COMPLETED" || parsed.type === "ERROR" || parsed.type === "CANCELLED") {
        void fetchVulnerabilityDbStatus().then(setDbStatus).catch(() => undefined);
        setIsSyncing(false);
      }
    };
    ["STARTED", "PROGRESS", "LOG", "WARNING", "ERROR", "COMPLETED", "CANCELLED"].forEach((type) =>
      stream.addEventListener(type, onEvent),
    );
    stream.onerror = () => stream.close();

    return () => {
      cancelled = true;
      window.clearInterval(interval);
      stream.close();
    };
  }, []);

  const latestSyncEvent = syncEvents.at(-1);
  const dbBadge = useMemo(() => {
    if (!dbStatus) return { label: "Checking DB", variant: "neutral" as const };
    if (dbStatus.isUpdating) return { label: "Sync in progress", variant: "info" as const };
    if (dbStatus.available) return { label: "Database ready", variant: "low" as const };
    return { label: "Database attention needed", variant: "medium" as const };
  }, [dbStatus]);

  async function refresh() {
    const [settings, status] = await Promise.all([fetchNvdSettings(), fetchVulnerabilityDbStatus()]);
    setNvdSettings(settings);
    setDbStatus(status);
  }

  async function handleSave() {
    try {
      setIsSaving(true);
      const saved = await saveNvdSettings(apiKey);
      setNvdSettings(saved);
      setApiKey("");
      toast.success("NVD API key saved locally");
      await refresh();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to save NVD API key");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleTest() {
    try {
      setIsTesting(true);
      const result = await testNvdSettings();
      if (result.valid) {
        toast.success(result.message);
      } else {
        toast.error(result.message);
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to test NVD API key");
    } finally {
      setIsTesting(false);
    }
  }

  async function handleRemove() {
    try {
      setIsRemoving(true);
      await deleteNvdSettings();
      setApiKey("");
      toast.success("NVD API key removed");
      await refresh();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to remove NVD API key");
    } finally {
      setIsRemoving(false);
    }
  }

  async function handleSync() {
    try {
      setIsSyncing(true);
      setSyncEvents([]);
      const status = await syncVulnerabilityDb();
      setDbStatus(status);
      toast.success("Dependency-Check sync started");
    } catch (error) {
      setIsSyncing(false);
      toast.error(error instanceof Error ? error.message : "Unable to start vulnerability DB sync");
    }
  }

  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
      <div className="space-y-6">
        <Card className="overflow-hidden border-white/60 bg-gradient-to-br from-card to-card/80">
          <CardHeader>
            <div className="flex flex-wrap items-center gap-3">
              <Badge variant="info" className="gap-2">
                <KeyRound className="h-3.5 w-3.5" />
                Optional acceleration
              </Badge>
              {nvdSettings?.configured ? (
                <Badge variant="low">Configured {nvdSettings.maskedKey}</Badge>
              ) : (
                <Badge variant="neutral">Not configured</Badge>
              )}
            </div>
            <CardTitle>NVD API key</CardTitle>
            <CardDescription>
              An NVD API key is optional, but it can make Dependency-Check database updates much faster. The key is stored locally, masked in the UI, and never included in reports or streamed logs.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="rounded-[28px] border border-amber-500/20 bg-amber-500/10 px-4 py-4 text-sm text-amber-900 dark:text-amber-200">
              Treat the Docker host and persisted `jarscan-data` volume as sensitive if you configure a real NVD API key.
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              {[
                ["Storage mode", nvdSettings?.storageMode ?? "Loading..."],
                ["Updated", formatTimestamp(nvdSettings?.updatedAt ?? null)],
              ].map(([label, value]) => (
                <div key={String(label)} className="rounded-2xl border border-border/70 bg-background/60 px-4 py-4">
                  <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{label}</div>
                  <div className="mt-2 break-all text-sm font-medium">{value}</div>
                </div>
              ))}
            </div>

            <div className="space-y-3">
              <label className="block text-sm font-medium">NVD API key</label>
              <input
                type="password"
                value={apiKey}
                onChange={(event) => setApiKey(event.target.value)}
                placeholder={nvdSettings?.configured ? "Enter a replacement key" : "Paste your NVD API key"}
                className="w-full rounded-full border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary"
              />
              <p className="text-sm text-muted-foreground">
                After saving, the raw key is not returned to the frontend. Only a masked suffix is shown.
              </p>
            </div>

            <div className="flex flex-wrap gap-3">
              <Button onClick={handleSave} disabled={isSaving || !apiKey.trim()}>
                {isSaving ? "Saving..." : "Save key"}
              </Button>
              <Button variant="outline" onClick={handleTest} disabled={isTesting}>
                {isTesting ? "Testing..." : "Test key"}
              </Button>
              <Button variant="outline" onClick={handleRemove} disabled={isRemoving || !nvdSettings?.configured}>
                {isRemoving ? "Removing..." : "Remove key"}
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="space-y-6">
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center gap-3">
              <Badge variant={dbBadge.variant} className="gap-2">
                <DatabaseZap className="h-3.5 w-3.5" />
                {dbBadge.label}
              </Badge>
              {dbStatus?.apiKeyConfigured ? <Badge variant="low">API key detected</Badge> : <Badge variant="neutral">No API key</Badge>}
            </div>
            <CardTitle>Vulnerability database</CardTitle>
            <CardDescription>
              Sync Dependency-Check data on demand and inspect the current local DB status used by vulnerability scans.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="grid gap-3 md:grid-cols-2">
              {[
                ["CLI version", dbStatus?.cliVersion ?? "Unknown"],
                ["Data directory", dbStatus?.dataDirectory ?? "Unknown"],
                ["Last updated", formatTimestamp(dbStatus?.lastUpdated ?? null)],
                ["Last sync status", dbStatus?.lastSyncStatus ?? "Unknown"],
                ["Last sync completed", formatTimestamp(dbStatus?.lastSyncCompletedAt ?? null)],
                ["Last sync duration", formatDuration(dbStatus?.lastSyncDurationMs ?? null)],
              ].map(([label, value]) => (
                <div key={String(label)} className="rounded-2xl border border-border/70 bg-background/60 px-4 py-4">
                  <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{label}</div>
                  <div className="mt-2 break-all text-sm font-medium">{value}</div>
                </div>
              ))}
            </div>

            {dbStatus?.lastSyncError ? (
              <div className="rounded-[28px] border border-rose-500/20 bg-rose-500/10 px-4 py-4 text-sm text-rose-700 dark:text-rose-300">
                {dbStatus.lastSyncError}
              </div>
            ) : null}

            <div className="flex flex-wrap gap-3">
              <Button onClick={handleSync} disabled={isSyncing || dbStatus?.isUpdating}>
                <RefreshCw className={`mr-2 h-4 w-4 ${isSyncing || dbStatus?.isUpdating ? "animate-spin" : ""}`} />
                Sync now
              </Button>
              <Button variant="outline" onClick={() => void refresh()}>
                <ShieldCheck className="mr-2 h-4 w-4" />
                Refresh status
              </Button>
            </div>

            <div className="rounded-[28px] border border-border/70 bg-slate-950 p-4 text-xs text-slate-200">
              <div className="mb-3 flex items-center justify-between">
                <div className="font-medium text-slate-100">Sync log</div>
                <div className="text-slate-400">{latestSyncEvent?.message ?? "Waiting for sync activity"}</div>
              </div>
              <AnimatePresence initial={false}>
                {syncEvents.length === 0 ? (
                  <div className="text-slate-400">
                    Sync events will appear here when a manual DB update is running.
                  </div>
                ) : (
                  syncEvents.map((event) => (
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
                      <span>{event.message}</span>
                    </motion.div>
                  ))
                )}
              </AnimatePresence>
            </div>
          </CardContent>
        </Card>
      </div>
    </motion.div>
  );
}
