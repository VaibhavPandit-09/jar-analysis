import { createBrowserRouter, RouterProvider } from "react-router-dom";

import { AppShell } from "@/components/app-shell";
import { JobProgressPage } from "@/pages/job-progress-page";
import { ResultsPage } from "@/pages/results-page";
import { UploadPage } from "@/pages/upload-page";

const router = createBrowserRouter([
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <UploadPage /> },
      { path: "jobs/:jobId", element: <JobProgressPage /> },
      { path: "jobs/:jobId/results", element: <ResultsPage /> },
    ],
  },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
