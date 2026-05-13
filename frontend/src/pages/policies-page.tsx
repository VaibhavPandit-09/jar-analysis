import { motion } from "framer-motion";
import { PencilLine, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { deletePolicy, fetchPolicies, updatePolicy } from "@/lib/api";
import type { PolicyRecord } from "@/lib/types";

export function PoliciesPage() {
  const [policies, setPolicies] = useState<PolicyRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draftDescription, setDraftDescription] = useState("");
  const [draftConfig, setDraftConfig] = useState("{}");
  const [target, setTarget] = useState<PolicyRecord | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        setIsLoading(true);
        const response = await fetchPolicies();
        if (!cancelled) {
          setPolicies(response);
        }
      } catch (error) {
        if (!cancelled) {
          toast.error(error instanceof Error ? error.message : "Unable to load policies");
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

  function beginEdit(policy: PolicyRecord) {
    setEditingId(policy.id);
    setDraftDescription(policy.description ?? "");
    setDraftConfig(JSON.stringify(policy.config, null, 2));
  }

  async function toggleEnabled(policy: PolicyRecord) {
    try {
      const updated = await updatePolicy(policy.id, { enabled: !policy.enabled });
      setPolicies((current) => current.map((item) => (item.id === policy.id ? updated : item)));
      toast.success(updated.enabled ? "Policy enabled" : "Policy disabled");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to update policy");
    }
  }

  async function savePolicy(policy: PolicyRecord) {
    try {
      const config = JSON.parse(draftConfig) as Record<string, unknown>;
      const updated = await updatePolicy(policy.id, {
        description: draftDescription,
        config,
      });
      setPolicies((current) => current.map((item) => (item.id === policy.id ? updated : item)));
      setEditingId(null);
      toast.success("Policy updated");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Policy config must be valid JSON");
    }
  }

  async function confirmDelete() {
    if (!target) return;
    try {
      await deletePolicy(target.id);
      setPolicies((current) => current.filter((item) => item.id !== target.id));
      setTarget(null);
      toast.success("Policy deleted");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to delete policy");
    }
  }

  return (
    <>
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
        <Card className="overflow-hidden border-white/60 bg-gradient-to-br from-card to-card/80">
          <CardHeader>
            <CardTitle>Policies</CardTitle>
            <CardDescription>
              Built-in policies are stored in SQLite and evaluated against reopened scans. You can disable them, adjust simple config JSON, and use suppressions when a warning or failure is intentionally accepted.
            </CardDescription>
          </CardHeader>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Configured policies</CardTitle>
            <CardDescription>
              The default set covers vulnerabilities, licenses, duplicate classes, usage cleanup signals, Java version limits, snapshots, and broad bundles.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {isLoading ? (
              <div className="text-sm text-muted-foreground">Loading policies...</div>
            ) : (
              policies.map((policy) => (
                <div key={policy.id} className="rounded-3xl border border-border/70 bg-background/70 p-5">
                  <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                    <div className="space-y-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <div className="text-lg font-semibold">{policy.name}</div>
                        <Badge variant={policy.enabled ? "low" : "neutral"}>{policy.enabled ? "Enabled" : "Disabled"}</Badge>
                        <Badge variant={policy.severity === "FAIL" ? "critical" : "medium"}>{policy.severity}</Badge>
                      </div>
                      <div className="text-sm text-muted-foreground">{policy.description ?? "No description"}</div>
                      <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{policy.ruleType}</div>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <Button variant="outline" size="sm" onClick={() => void toggleEnabled(policy)}>
                        {policy.enabled ? "Disable" : "Enable"}
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => beginEdit(policy)}>
                        <PencilLine className="mr-2 h-4 w-4" /> Edit
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => setTarget(policy)}>
                        <Trash2 className="mr-2 h-4 w-4" /> Delete
                      </Button>
                    </div>
                  </div>

                  {editingId === policy.id ? (
                    <div className="mt-5 space-y-4">
                      <label className="block space-y-2 text-sm">
                        <span className="font-medium">Description</span>
                        <textarea
                          value={draftDescription}
                          onChange={(event) => setDraftDescription(event.target.value)}
                          rows={3}
                          className="w-full rounded-3xl border border-border bg-background px-4 py-3 outline-none"
                        />
                      </label>
                      <label className="block space-y-2 text-sm">
                        <span className="font-medium">Config JSON</span>
                        <textarea
                          value={draftConfig}
                          onChange={(event) => setDraftConfig(event.target.value)}
                          rows={8}
                          className="w-full rounded-3xl border border-border bg-slate-950 px-4 py-3 font-mono text-xs text-slate-200 outline-none"
                        />
                      </label>
                      <div className="flex flex-wrap justify-end gap-3">
                        <Button variant="outline" onClick={() => setEditingId(null)}>Cancel</Button>
                        <Button onClick={() => void savePolicy(policy)}>Save changes</Button>
                      </div>
                    </div>
                  ) : null}
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </motion.div>

      <ConfirmDialog
        open={Boolean(target)}
        title="Delete policy?"
        description="This removes the policy definition from JARScan. Reopened scans will no longer evaluate it until the policy is re-created."
        confirmLabel="Delete policy"
        onConfirm={() => void confirmDelete()}
        onCancel={() => setTarget(null)}
      />
    </>
  );
}
