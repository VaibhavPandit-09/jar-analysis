import { createBrowserRouter, RouterProvider } from "react-router-dom";

import { AppShell } from "@/components/app-shell";
import { JobProgressPage } from "@/pages/job-progress-page";
import { ResultsPage } from "@/pages/results-page";
import { ScanHistoryPage } from "@/pages/scan-history-page";
import { SettingsPage } from "@/pages/settings-page";
import { UploadPage } from "@/pages/upload-page";
import { ComparePage } from "@/pages/compare-page";
import { SuppressionsPage } from "@/pages/suppressions-page";
import { PoliciesPage } from "@/pages/policies-page";

const router = createBrowserRouter([
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <UploadPage /> },
      { path: "scan-history", element: <ScanHistoryPage /> },
      { path: "compare", element: <ComparePage /> },
      { path: "settings", element: <SettingsPage /> },
      { path: "suppressions", element: <SuppressionsPage /> },
      { path: "policies", element: <PoliciesPage /> },
      { path: "jobs/:jobId", element: <JobProgressPage /> },
      { path: "jobs/:jobId/results", element: <ResultsPage /> },
      { path: "scans/:scanId/results", element: <ResultsPage /> },
    ],
  },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
