import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";

import { PoliciesPage } from "@/pages/policies-page";

const apiMocks = vi.hoisted(() => ({
  fetchPolicies: vi.fn(),
  updatePolicy: vi.fn(),
  deletePolicy: vi.fn(),
}));

vi.mock("@/lib/api", () => ({
  fetchPolicies: apiMocks.fetchPolicies,
  updatePolicy: apiMocks.updatePolicy,
  deletePolicy: apiMocks.deletePolicy,
}));

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

describe("PoliciesPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("renders policies and toggles enablement", async () => {
    apiMocks.fetchPolicies.mockResolvedValue([
      {
        id: "critical-vulnerabilities",
        name: "Fail if critical vulnerabilities exist",
        description: "Fails the scan review if critical vulnerabilities remain.",
        ruleType: "CRITICAL_VULNERABILITIES",
        severity: "FAIL",
        enabled: true,
        config: { threshold: 1 },
        createdAt: "2026-05-13T00:00:00Z",
        updatedAt: "2026-05-13T00:00:00Z",
      },
    ]);
    apiMocks.updatePolicy.mockResolvedValue({
      id: "critical-vulnerabilities",
      name: "Fail if critical vulnerabilities exist",
      description: "Fails the scan review if critical vulnerabilities remain.",
      ruleType: "CRITICAL_VULNERABILITIES",
      severity: "FAIL",
      enabled: false,
      config: { threshold: 1 },
      createdAt: "2026-05-13T00:00:00Z",
      updatedAt: "2026-05-13T00:01:00Z",
    });

    render(
      <MemoryRouter>
        <PoliciesPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText(/Fail if critical vulnerabilities exist/i)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /disable/i }));
    await waitFor(() => expect(apiMocks.updatePolicy).toHaveBeenCalledWith("critical-vulnerabilities", { enabled: false }));
    expect(await screen.findByText(/Disabled/i)).toBeInTheDocument();
  });
});
