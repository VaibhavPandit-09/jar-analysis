import { motion } from "framer-motion";
import { DatabaseZap, RefreshCw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { toast } from "sonner";

import { ThemeToggle } from "@/components/theme-toggle";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { fetchVulnerabilityDbStatus, syncVulnerabilityDb } from "@/lib/api";
import type { VulnerabilityDbStatus } from "@/lib/types";

type BadgeVariant = "neutral" | "critical" | "high" | "medium" | "low" | "info";

function formatStatus(status: VulnerabilityDbStatus | null) {
  if (!status) return "Checking vulnerability database";
  if (status.isUpdating) return "Updating vulnerability database";
  return status.message;
}

export function AppShell() {
  const [dbStatus, setDbStatus] = useState<VulnerabilityDbStatus | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const status = await fetchVulnerabilityDbStatus();
        if (!cancelled) {
          setDbStatus(status);
        }
      } catch (error) {
        if (!cancelled) {
          toast.error(error instanceof Error ? error.message : "Unable to fetch vulnerability DB status");
        }
      }
    };

    void load();
    const interval = window.setInterval(load, 30000);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, []);

  const badgeVariant = useMemo<BadgeVariant>(() => {
    if (!dbStatus) return "neutral";
    if (dbStatus.isUpdating) return "info";
    return dbStatus.available ? "low" : "medium";
  }, [dbStatus]);

  async function handleSync() {
    try {
      setIsRefreshing(true);
      const status = await syncVulnerabilityDb();
      setDbStatus(status);
      toast.success("Vulnerability database sync started");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to sync vulnerability DB");
    } finally {
      setIsRefreshing(false);
    }
  }

  return (
    <div className="relative min-h-screen overflow-hidden">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-[520px] bg-[radial-gradient(circle_at_top_left,_rgba(15,118,110,0.22),_transparent_44%),radial-gradient(circle_at_top_right,_rgba(245,158,11,0.18),_transparent_32%),linear-gradient(180deg,_rgba(255,255,255,0.72),_transparent)] dark:bg-[radial-gradient(circle_at_top_left,_rgba(45,212,191,0.18),_transparent_42%),radial-gradient(circle_at_top_right,_rgba(249,115,22,0.16),_transparent_30%),linear-gradient(180deg,_rgba(9,14,28,0.92),_transparent)]" />
      <div className="mx-auto flex min-h-screen w-full max-w-7xl flex-col px-4 pb-14 pt-6 sm:px-6 lg:px-8">
        <motion.header
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-8 flex flex-col gap-6 rounded-[32px] border border-white/60 bg-background/75 px-6 py-5 shadow-[0_24px_64px_rgba(21,41,91,0.12)] backdrop-blur-2xl dark:border-white/10 dark:bg-slate-950/65"
        >
          <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
            <div className="space-y-2">
              <NavLink to="/" className="inline-flex items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-primary to-cyan-400 text-lg font-bold text-white shadow-lg shadow-primary/25">
                  JS
                </div>
                <div>
                  <h1 className="font-display text-3xl font-bold tracking-tight text-foreground">JARScan</h1>
                  <p className="text-sm text-muted-foreground">
                    Java artifact and dependency security analyzer
                  </p>
                </div>
              </NavLink>
              <nav className="flex flex-wrap gap-2 pt-2">
                {[
                  { to: "/", label: "Upload", end: true },
                  { to: "/scan-history", label: "Scan History" },
                  { to: "/compare", label: "Compare" },
                  { to: "/settings", label: "Settings" },
                ].map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      [
                        "rounded-full border px-4 py-2 text-sm font-medium transition",
                        isActive
                          ? "border-primary/30 bg-primary/10 text-primary"
                          : "border-border bg-background/60 text-muted-foreground hover:bg-accent hover:text-accent-foreground",
                      ].join(" ")
                    }
                  >
                    {item.label}
                  </NavLink>
                ))}
              </nav>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <Badge variant={badgeVariant} className="gap-2 px-4 py-2 text-[11px] uppercase tracking-[0.2em]">
                <DatabaseZap className="h-3.5 w-3.5" />
                {formatStatus(dbStatus)}
              </Badge>
              <Button variant="outline" size="sm" onClick={handleSync} disabled={isRefreshing}>
                <RefreshCw className={`mr-2 h-4 w-4 ${isRefreshing ? "animate-spin" : ""}`} />
                Sync DB
              </Button>
              <ThemeToggle />
            </div>
          </div>
        </motion.header>

        <main className="flex-1">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
