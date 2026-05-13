import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { CreateSuppressionPayload } from "@/lib/types";

interface SuppressionDialogProps {
  open: boolean;
  title: string;
  description: string;
  targetLabel: string;
  initialPayload: Omit<CreateSuppressionPayload, "reason">;
  onClose: () => void;
  onSubmit: (payload: CreateSuppressionPayload) => Promise<void>;
}

export function SuppressionDialog({
  open,
  title,
  description,
  targetLabel,
  initialPayload,
  onClose,
  onSubmit,
}: SuppressionDialogProps) {
  const [reason, setReason] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setReason("");
      setExpiresAt(initialPayload.expiresAt ?? "");
      setIsSubmitting(false);
    }
  }, [initialPayload.expiresAt, open]);

  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4 backdrop-blur-sm">
      <Card className="w-full max-w-2xl shadow-2xl">
        <CardHeader>
          <CardTitle>{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="rounded-3xl border border-border/70 bg-background/70 px-4 py-4 text-sm">
            <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Target</div>
            <div className="mt-2 break-words font-medium">{targetLabel}</div>
          </div>

          <label className="block space-y-2 text-sm">
            <span className="font-medium">Reason</span>
            <textarea
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              rows={4}
              className="w-full rounded-3xl border border-border bg-background px-4 py-3 outline-none"
              placeholder="Explain why this finding is being suppressed"
            />
          </label>

          <label className="block space-y-2 text-sm">
            <span className="font-medium">Expiry date (optional)</span>
            <input
              type="date"
              value={expiresAt}
              onChange={(event) => setExpiresAt(event.target.value)}
              className="w-full rounded-full border border-border bg-background px-4 py-3 outline-none"
            />
          </label>

          <div className="rounded-3xl border border-amber-500/30 bg-amber-500/10 px-4 py-4 text-sm text-amber-900 dark:text-amber-100">
            Java dependencies may be used through reflection, configuration, ServiceLoader, Spring auto-configuration, servlet containers, logging frameworks, JDBC drivers, or runtime plugin loading. Suppressions should be reviewed and revisited intentionally.
          </div>

          <div className="flex flex-wrap justify-end gap-3">
            <Button variant="outline" onClick={onClose} disabled={isSubmitting}>Cancel</Button>
            <Button
              onClick={async () => {
                setIsSubmitting(true);
                try {
                  await onSubmit({
                    ...initialPayload,
                    reason: reason.trim(),
                    expiresAt: expiresAt ? `${expiresAt}T00:00:00Z` : null,
                  });
                  onClose();
                } finally {
                  setIsSubmitting(false);
                }
              }}
              disabled={isSubmitting || !reason.trim()}
            >
              {isSubmitting ? "Saving..." : "Create suppression"}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
