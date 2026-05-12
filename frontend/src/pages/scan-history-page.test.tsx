import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";

import { ScanHistoryPage } from "@/pages/scan-history-page";
import type { StoredScanSummary } from "@/lib/types";

const apiMocks = vi.hoisted(() => ({
  fetchScans: vi.fn(),
  deleteStoredScan: vi.fn(),
  updateStoredScan: vi.fn(),
}));

vi.mock("@/lib/api", () => ({
  fetchScans: apiMocks.fetchScans,
  deleteStoredScan: apiMocks.deleteStoredScan,
  updateStoredScan: apiMocks.updateStoredScan,
  buildJobExportUrl: (jobId: string, format: string) => `/api/jobs/${jobId}/export?format=${format}`,
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

const baseScan: StoredScanSummary = {
  scanId: "scan-1",
  jobId: "job-1",
  inputType: "ARCHIVE_UPLOAD",
  inputName: "spring-boot-app.jar",
  inputHash: "hash-1",
  status: "COMPLETED",
  startedAt: "2026-05-12T10:00:00Z",
  completedAt: "2026-05-12T10:01:00Z",
  durationMs: 60000,
  totalArtifacts: 5,
  totalDependencies: 18,
  totalVulnerabilities: 4,
  criticalCount: 1,
  highCount: 2,
  mediumCount: 1,
  lowCount: 0,
  infoCount: 0,
  unknownCount: 0,
  highestCvss: 9.8,
  requiredJavaVersion: "Java 21",
  highestSeverity: "CRITICAL",
  createdAppVersion: "0.1.0-SNAPSHOT",
  notes: "Investigate before release",
  tags: ["release", "spring"],
  createdAt: "2026-05-12T10:01:00Z",
  updatedAt: "2026-05-12T10:01:00Z",
};

function renderPage() {
  return render(
    <MemoryRouter>
      <ScanHistoryPage />
    </MemoryRouter>,
  );
}

describe("ScanHistoryPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders persisted scans and supports search filtering", async () => {
    apiMocks.fetchScans.mockResolvedValue([
      baseScan,
      {
        ...baseScan,
        scanId: "scan-2",
        jobId: "job-2",
        inputName: "legacy-service.jar",
        highestSeverity: "HIGH",
        criticalCount: 0,
        highCount: 1,
        totalVulnerabilities: 1,
      },
    ]);

    renderPage();

    expect(await screen.findByText("spring-boot-app.jar")).toBeInTheDocument();
    expect(screen.getByText("legacy-service.jar")).toBeInTheDocument();

    await userEvent.type(screen.getByPlaceholderText("Search by input, note, tag, or job ID"), "legacy");

    await waitFor(() => {
      expect(screen.queryByText("spring-boot-app.jar")).not.toBeInTheDocument();
    });
    expect(screen.getByText("legacy-service.jar")).toBeInTheDocument();
  });

  it("shows an empty state when no scans are stored", async () => {
    apiMocks.fetchScans.mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText("No scans found")).toBeInTheDocument();
    expect(screen.getByText("Run an analysis and completed scans will appear here.")).toBeInTheDocument();
  });

  it("confirms deletion and removes the scan from the list", async () => {
    apiMocks.fetchScans.mockResolvedValue([baseScan]);
    apiMocks.deleteStoredScan.mockResolvedValue(undefined);

    renderPage();

    expect(await screen.findByText("spring-boot-app.jar")).toBeInTheDocument();

    const deleteButton = screen.getByRole("button", { name: /^delete$/i });
    await userEvent.click(deleteButton);

    expect(await screen.findByText("Delete stored scan?")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Delete scan" }));

    await waitFor(() => {
      expect(apiMocks.deleteStoredScan).toHaveBeenCalledWith("scan-1");
    });
    await waitFor(() => {
      expect(screen.queryByText("spring-boot-app.jar")).not.toBeInTheDocument();
    });
  });

  it("links completed scans to the reopened results route", async () => {
    apiMocks.fetchScans.mockResolvedValue([baseScan]);

    renderPage();

    const openLink = await screen.findByRole("link", { name: /open scan/i });
    expect(openLink).toHaveAttribute("href", "/scans/scan-1/results");

    const exportJson = screen.getByRole("link", { name: /export json/i });
    expect(exportJson).toHaveAttribute("href", "/api/jobs/job-1/export?format=json");
  });
});
