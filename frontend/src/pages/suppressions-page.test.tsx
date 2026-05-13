import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";

import { SuppressionsPage } from "@/pages/suppressions-page";

const apiMocks = vi.hoisted(() => ({
  fetchSuppressions: vi.fn(),
  updateSuppression: vi.fn(),
  deleteSuppression: vi.fn(),
}));

vi.mock("@/lib/api", () => ({
  fetchSuppressions: apiMocks.fetchSuppressions,
  updateSuppression: apiMocks.updateSuppression,
  deleteSuppression: apiMocks.deleteSuppression,
}));

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

describe("SuppressionsPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("renders suppressions and pauses one", async () => {
    apiMocks.fetchSuppressions.mockResolvedValue([
      {
        id: "sup-1",
        type: "VULNERABILITY",
        groupId: "org.example",
        artifactId: "demo",
        version: "1.0.0",
        cveId: "CVE-2026-4242",
        reason: "Accepted risk",
        expiresAt: null,
        active: true,
        createdAt: "2026-05-13T00:00:00Z",
        updatedAt: "2026-05-13T00:00:00Z",
      },
    ]);
    apiMocks.updateSuppression.mockResolvedValue({
      id: "sup-1",
      type: "VULNERABILITY",
      groupId: "org.example",
      artifactId: "demo",
      version: "1.0.0",
      cveId: "CVE-2026-4242",
      reason: "Accepted risk",
      expiresAt: null,
      active: false,
      createdAt: "2026-05-13T00:00:00Z",
      updatedAt: "2026-05-13T00:01:00Z",
    });

    render(
      <MemoryRouter>
        <SuppressionsPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText(/Accepted risk/i)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /pause/i }));
    await waitFor(() => expect(apiMocks.updateSuppression).toHaveBeenCalledWith("sup-1", { active: false }));
    expect(await screen.findByText(/Paused/i)).toBeInTheDocument();
  });
});
