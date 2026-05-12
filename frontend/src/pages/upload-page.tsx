import { motion } from "framer-motion";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

import { UploadZone } from "@/components/upload-zone";
import { createAnalysisJob } from "@/lib/api";

function validateSelection(files: File[]) {
  const pomFiles = files.filter((file) => file.name.toLowerCase() === "pom.xml");
  if (pomFiles.length > 1) {
    return "Only one pom.xml can be uploaded at a time.";
  }
  if (pomFiles.length === 1 && files.length > 1) {
    return "Upload either one pom.xml or one or more archives, not both together.";
  }
  return null;
}

export function UploadPage() {
  const navigate = useNavigate();
  const [files, setFiles] = useState<File[]>([]);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function updateFiles(nextFiles: File[]) {
    const combined = [...files];
    nextFiles.forEach((file) => {
      if (!combined.some((existing) => existing.name === file.name && existing.size === file.size)) {
        combined.push(file);
      }
    });
    setFiles(combined);
    setValidationError(validateSelection(combined));
  }

  function removeFile(name: string) {
    const nextFiles = files.filter((file) => file.name !== name);
    setFiles(nextFiles);
    setValidationError(validateSelection(nextFiles));
  }

  async function handleAnalyze() {
    const error = validateSelection(files);
    setValidationError(error);
    if (error) return;

    try {
      setIsSubmitting(true);
      const { jobId } = await createAnalysisJob(files);
      toast.success("Analysis job started");
      navigate(`/jobs/${jobId}`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Unable to start analysis");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="space-y-8">
      <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <UploadZone
          files={files}
          onFilesSelected={updateFiles}
          onRemove={removeFile}
          onAnalyze={handleAnalyze}
          analyzing={isSubmitting}
          validationError={validationError}
        />

        <div className="grid gap-4">
          {[
            {
              title: "Manifest and bytecode",
              copy: "Inspect entry count, hashes, Java class versions, module metadata, and manifest attributes without executing any uploaded code.",
            },
            {
              title: "Maven-aware identity",
              copy: "Extract embedded `pom.properties`, parse `pom.xml`, and map resolved dependencies into vulnerability findings.",
            },
            {
              title: "Fat JAR decomposition",
              copy: "Spring Boot and similar bundled archives are unpacked recursively so nested libraries are visible instead of hidden.",
            },
          ].map((item) => (
            <div
              key={item.title}
              className="rounded-[28px] border border-border/70 bg-background/70 px-5 py-5 shadow-sm"
            >
              <h3 className="font-display text-lg font-semibold">{item.title}</h3>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">{item.copy}</p>
            </div>
          ))}
        </div>
      </section>
    </motion.div>
  );
}
