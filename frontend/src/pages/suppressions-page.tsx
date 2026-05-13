import { motion } from "framer-motion";
import { Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { deleteSuppression, fetchSuppressions, updateSuppression } from "@/lib/api";
import type { SuppressionRecord } from "@/lib/types";

export function SuppressionsPage() {
  const [suppressions, setSuppressions] = useState<SuppressionRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [target, setTarget] = useState<SuppressionRecord | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        setIsLoading(true);
        const response = await fetchSuppressions();
        if (!cancelled) {
          setSuppressions(response);
        }
      } catch (error) {
        if (!cancelled) {
          toast.error(error instanceof Error ? error.message : "Unable to load suppressions");
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

  async function toggleActive(record: SuppressionRecord) {
    try {
      const updated = await updateSuppression(record.id, { active: !record.active });
      setSuppressions((current) => current.map((item) => (item.id === record.id ? updated : item)));
      toast.success(updated.active ? "Suppression enabled" : "Suppression paused");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to update suppression");
    }
  }

  async function confirmDelete() {
    if (!target) return;
    try {
      await deleteSuppression(target.id);
      setSuppressions((current) => current.filter((item) => item.id !== target.id));
      toast.success("Suppression deleted");
      setTarget(null);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to delete suppression");
    }
  }

  return (
    <>
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
        <Card className="overflow-hidden border-white/60 bg-gradient-to-br from-card to-card/80">
          <CardHeader>
            <CardTitle>Suppressions</CardTitle>
            <CardDescription>
              Review, pause, or remove persisted suppressions. JARScan keeps raw findings intact and marks matching results as suppressed when the suppression is still active and not expired.
            </CardDescription>
          </CardHeader>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Active and historical suppressions</CardTitle>
            <CardDescription>
              Use this page to audit accepted risk, keep reasons attached to findings, and clean up suppressions that are no longer needed.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="text-sm text-muted-foreground">Loading suppressions...</div>
            ) : suppressions.length === 0 ? (
              <div className="rounded-3xl border border-border/70 bg-background/70 px-4 py-6 text-sm text-muted-foreground">
                No suppressions have been created yet.
              </div>
            ) : (
              <div className="overflow-hidden rounded-3xl border border-border/70">
                <table className="min-w-full divide-y divide-border/70 text-sm">
                  <thead className="bg-secondary/60 text-left">
                    <tr>
                      {[
                        "Type",
                        "Target",
                        "Reason",
                        "Expiry",
                        "Status",
                        "Actions",
                      ].map((header) => (
                        <th key={header} className="px-4 py-3 font-medium text-muted-foreground">{header}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60 bg-background/70">
                    {suppressions.map((record) => (
                      <tr key={record.id}>
                        <td className="px-4 py-3 align-top"><Badge variant="neutral">{record.type}</Badge></td>
                        <td className="px-4 py-3 align-top">
                          <div className="font-medium">{record.groupId ?? record.type}</div>
                          <div className="text-muted-foreground">{[record.artifactId, record.version, record.cveId].filter(Boolean).join(" • ") || "General suppression"}</div>
                        </td>
                        <td className="px-4 py-3 align-top text-muted-foreground">{record.reason}</td>
                        <td className="px-4 py-3 align-top">{record.expiresAt ? new Date(record.expiresAt).toLocaleDateString() : "No expiry"}</td>
                        <td className="px-4 py-3 align-top">
                          <Badge variant={record.active ? "low" : "neutral"}>{record.active ? "Active" : "Paused"}</Badge>
                        </td>
                        <td className="px-4 py-3 align-top">
                          <div className="flex flex-wrap gap-2">
                            <Button variant="outline" size="sm" onClick={() => void toggleActive(record)}>
                              {record.active ? "Pause" : "Enable"}
                            </Button>
                            <Button variant="outline" size="sm" onClick={() => setTarget(record)}>
                              <Trash2 className="mr-2 h-4 w-4" /> Delete
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      </motion.div>

      <ConfirmDialog
        open={Boolean(target)}
        title="Delete suppression?"
        description="This removes the suppression record entirely. Raw findings were never deleted, so affected results will simply show the finding again."
        confirmLabel="Delete suppression"
        onConfirm={() => void confirmDelete()}
        onCancel={() => setTarget(null)}
      />
    </>
  );
}
