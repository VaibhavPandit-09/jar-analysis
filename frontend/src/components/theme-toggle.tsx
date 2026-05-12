import { LaptopMinimal, MoonStar, SunMedium } from "lucide-react";
import { useTheme } from "next-themes";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";

export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) {
    return <div className="h-10 w-[146px] rounded-full border border-border bg-background/70" />;
  }

  return (
    <TooltipProvider>
      <div className="flex items-center rounded-full border border-border bg-background/70 p-1">
        {[
          { label: "System", value: "system", icon: LaptopMinimal },
          { label: "Light", value: "light", icon: SunMedium },
          { label: "Dark", value: "dark", icon: MoonStar },
        ].map((option) => (
          <Tooltip key={option.value}>
            <TooltipTrigger asChild>
              <Button
                variant={theme === option.value ? "default" : "ghost"}
                size="sm"
                onClick={() => setTheme(option.value)}
                className="h-8 rounded-full px-3"
                aria-label={`Switch to ${option.label.toLowerCase()} mode`}
              >
                <option.icon className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{option.label}</TooltipContent>
          </Tooltip>
        ))}
      </div>
    </TooltipProvider>
  );
}
