import { AnimatePresence, motion } from "framer-motion";
import { FileArchive, FileCode2, FileX, Sparkles, UploadCloud } from "lucide-react";
import { useMemo } from "react";
import { useDropzone } from "react-dropzone";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

const ACCEPTED = {
  "application/java-archive": [".jar"],
  "application/zip": [".zip"],
  "application/x-zip-compressed": [".zip"],
  "application/json": [".json", ".cdx.json", ".bom.json"],
  "application/xml": [".xml"],
  "text/xml": [".xml"],
  "text/plain": [".json", ".cdx.json", ".bom.json"],
  "application/octet-stream": [".jar", ".war", ".ear", ".zip", ".json", ".cdx.json", ".bom.json"],
};

interface UploadZoneProps {
  files: File[];
  onFilesSelected: (files: File[]) => void;
  onRemove: (name: string) => void;
  onAnalyze: () => void;
  analyzing: boolean;
  validationError: string | null;
}

export function UploadZone({
  files,
  onFilesSelected,
  onRemove,
  onAnalyze,
  analyzing,
  validationError,
}: UploadZoneProps) {
  const totalSizeMb = useMemo(
    () => `${(files.reduce((sum, file) => sum + file.size, 0) / (1024 * 1024)).toFixed(1)} MB`,
    [files],
  );

  const { getRootProps, getInputProps, isDragActive, open } = useDropzone({
    accept: ACCEPTED,
    multiple: true,
    noClick: true,
    onDrop: onFilesSelected,
  });

  return (
    <Card className="overflow-hidden border-white/60 bg-gradient-to-br from-card to-card/80">
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between gap-4">
          <div>
            <CardTitle>Drop Java archives, a Maven descriptor, or a project ZIP</CardTitle>
            <CardDescription>
              Archive mode accepts JAR/WAR/EAR files, POM mode accepts one <code>pom.xml</code>, and Project mode accepts one
              source or build ZIP for safe extraction and best-effort structure detection. CycloneDX JSON SBOMs can be imported directly into scan history.
            </CardDescription>
          </div>
          <Badge variant="info" className="hidden sm:inline-flex">
            <Sparkles className="mr-1 h-3.5 w-3.5" />
            Docker-first workflow
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        <div
          {...getRootProps()}
          className="relative rounded-[28px] border border-dashed border-border bg-gradient-to-br from-secondary/70 via-background to-primary/5 p-8"
          style={{
            transform: isDragActive ? "scale(1.01)" : undefined,
            borderColor: isDragActive ? "hsl(var(--primary))" : undefined,
          }}
        >
          <input {...getInputProps()} />
          <div className="mx-auto flex max-w-2xl flex-col items-center gap-4 text-center">
            <div className="flex h-20 w-20 items-center justify-center rounded-[28px] bg-primary/10 text-primary">
              <UploadCloud className="h-10 w-10" />
            </div>
            <div className="space-y-3">
              <h2 className="font-display text-2xl font-semibold tracking-tight">
                {isDragActive ? "Release files to queue analysis" : "Inspect archives, Maven builds, and project ZIPs"}
              </h2>
              <p className="mx-auto max-w-xl text-sm leading-6 text-muted-foreground">
                JARScan reads archive structure, Java bytecode versions, manifest data, embedded Maven metadata,
                nested dependencies, project structure hints, and local vulnerability findings without ever executing uploaded code.
              </p>
            </div>

            <div className="flex flex-wrap items-center justify-center gap-3">
              <Button onClick={open}>Browse files</Button>
              <Badge variant="neutral">Accepts `.jar`, `.war`, `.ear`, `.zip`, `pom.xml`, and CycloneDX `.json`</Badge>
            </div>
          </div>
        </div>

        <div className="grid gap-3 md:grid-cols-3">
          {[
            "Archive mode: upload one or more JAR/WAR/EAR files",
            "POM mode: upload one pom.xml for Maven dependency resolution",
            "Project mode: upload one ZIP for safe extraction and structure detection",
            "SBOM mode: import one CycloneDX JSON file into scan history",
          ].map((hint) => (
            <div key={hint} className="rounded-2xl border border-border/70 bg-secondary/40 px-4 py-3 text-sm text-muted-foreground">
              {hint}
            </div>
          ))}
        </div>

        {validationError ? (
          <div className="rounded-2xl border border-rose-500/20 bg-rose-500/8 px-4 py-3 text-sm text-rose-700 dark:text-rose-300">
            {validationError}
          </div>
        ) : null}

        <div className="space-y-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h3 className="font-semibold text-foreground">Selected files</h3>
              <p className="text-sm text-muted-foreground">
                {files.length ? `${files.length} file(s) ready, ${totalSizeMb}` : "No files selected yet"}
              </p>
            </div>
            <Button onClick={onAnalyze} disabled={!files.length || analyzing}>
              {analyzing ? "Starting..." : files.some((file) => file.name.toLowerCase().endsWith(".json")) ? "Import SBOM" : "Start analysis"}
            </Button>
          </div>

          <AnimatePresence initial={false}>
            <div className="grid gap-3">
              {files.length === 0 ? (
                <div className="rounded-2xl border border-border/70 bg-background/60 px-4 py-6 text-sm text-muted-foreground">
                  Add one or more Java archives, one <code>pom.xml</code>, or one project <code>.zip</code>.
                </div>
              ) : null}
              {files.length === 0 ? (
                <div className="rounded-2xl border border-border/70 bg-background/60 px-4 py-6 text-sm text-muted-foreground">
                  First Dependency-Check DB update can still be slow on a cold local cache.
                </div>
              ) : null}

              {files.map((file) => {
                const isPom = file.name.toLowerCase() === "pom.xml";
                const lowerName = file.name.toLowerCase();
                const isZip = lowerName.endsWith(".zip");
                const isSbom = lowerName.endsWith(".json") || lowerName.endsWith(".cdx.json") || lowerName.endsWith(".bom.json");
                return (
                  <motion.div
                    key={`${file.name}-${file.size}`}
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    className="flex items-center justify-between gap-4 rounded-2xl border border-border/70 bg-background/60 px-4 py-4"
                  >
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="rounded-2xl bg-secondary p-3 text-muted-foreground">
                        {isPom ? <FileCode2 className="h-5 w-5" /> : <FileArchive className="h-5 w-5" />}
                      </div>
                      <div className="min-w-0">
                        <div className="truncate font-medium text-foreground">{file.name}</div>
                        <div className="text-sm text-muted-foreground">
                          {(file.size / 1024 / 1024).toFixed(2)} MB {isSbom ? "• SBOM import" : isZip ? "• Project ZIP" : isPom ? "• POM mode" : "• Archive mode"}
                        </div>
                      </div>
                    </div>
                    <Button variant="ghost" size="icon" onClick={() => onRemove(file.name)}>
                      <FileX className="h-4 w-4" />
                    </Button>
                  </motion.div>
                );
              })}
            </div>
          </AnimatePresence>
        </div>
      </CardContent>
    </Card>
  );
}
