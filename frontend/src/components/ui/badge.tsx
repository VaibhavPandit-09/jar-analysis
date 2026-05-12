import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold tracking-wide",
  {
    variants: {
      variant: {
        neutral: "border-border bg-secondary text-secondary-foreground",
        critical: "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-300",
        high: "border-orange-500/30 bg-orange-500/10 text-orange-700 dark:text-orange-300",
        medium: "border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300",
        low: "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
        info: "border-sky-500/30 bg-sky-500/10 text-sky-700 dark:text-sky-300",
      },
    },
    defaultVariants: {
      variant: "neutral",
    },
  },
);

interface BadgeProps extends React.HTMLAttributes<HTMLDivElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />;
}
