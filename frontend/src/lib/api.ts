import type {
  AnalysisJobStatus,
  AnalysisResult,
  VulnerabilityDbStatus,
} from "./types";

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed with ${response.status}`);
  }
  return (await response.json()) as T;
}

export async function createAnalysisJob(files: File[]) {
  const formData = new FormData();
  files.forEach((file) => formData.append("files", file));

  return parseJson<{ jobId: string }>(
    await fetch("/api/jobs", {
      method: "POST",
      body: formData,
    }),
  );
}

export async function fetchJobStatus(jobId: string) {
  return parseJson<AnalysisJobStatus>(await fetch(`/api/jobs/${jobId}/status`));
}

export async function fetchJobResult(jobId: string) {
  return parseJson<AnalysisResult>(await fetch(`/api/jobs/${jobId}/result`));
}

export async function cancelJob(jobId: string) {
  return parseJson<AnalysisJobStatus>(
    await fetch(`/api/jobs/${jobId}/cancel`, { method: "POST" }),
  );
}

export async function fetchVulnerabilityDbStatus() {
  return parseJson<VulnerabilityDbStatus>(
    await fetch("/api/vulnerability-db/status"),
  );
}

export async function syncVulnerabilityDb() {
  return parseJson<VulnerabilityDbStatus>(
    await fetch("/api/vulnerability-db/sync", { method: "POST" }),
  );
}
