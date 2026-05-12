import type {
  AnalysisJobStatus,
  AnalysisResult,
  NvdSettingsStatus,
  NvdSettingsTestResponse,
  StoredScan,
  StoredScanQuery,
  StoredScanSummary,
  UpdateStoredScanPayload,
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

export function buildJobExportUrl(jobId: string, format: "json" | "markdown" | "html") {
  return `/api/jobs/${jobId}/export?format=${format}`;
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

export async function fetchNvdSettings() {
  return parseJson<NvdSettingsStatus>(await fetch("/api/settings/nvd"));
}

export async function saveNvdSettings(apiKey: string) {
  return parseJson<NvdSettingsStatus>(
    await fetch("/api/settings/nvd", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ apiKey }),
    }),
  );
}

export async function testNvdSettings() {
  return parseJson<NvdSettingsTestResponse>(
    await fetch("/api/settings/nvd/test", {
      method: "POST",
    }),
  );
}

export async function deleteNvdSettings() {
  const response = await fetch("/api/settings/nvd", { method: "DELETE" });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed with ${response.status}`);
  }
}

export async function fetchScans(query: StoredScanQuery = {}) {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, String(value));
    }
  });

  const suffix = params.toString() ? `?${params}` : "";
  return parseJson<StoredScanSummary[]>(await fetch(`/api/scans${suffix}`));
}

export async function fetchStoredScan(scanId: string) {
  return parseJson<StoredScan>(await fetch(`/api/scans/${scanId}`));
}

export async function deleteStoredScan(scanId: string) {
  const response = await fetch(`/api/scans/${scanId}`, { method: "DELETE" });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed with ${response.status}`);
  }
}

export async function updateStoredScan(scanId: string, payload: UpdateStoredScanPayload) {
  return parseJson<StoredScanSummary>(
    await fetch(`/api/scans/${scanId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    }),
  );
}
