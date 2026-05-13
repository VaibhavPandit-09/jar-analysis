import type { CreateSuppressionPayload } from "@/lib/types";

export interface SuppressionDraft {
  title: string;
  description: string;
  targetLabel: string;
  payload: Omit<CreateSuppressionPayload, "reason">;
}
