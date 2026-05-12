# Frontend Structure

## Route Structure

Current visible routes:

- `/`
- `/scan-history`
- `/settings`
- `/jobs/:jobId`
- `/jobs/:jobId/results`
- `/scans/:scanId/results`

These routes are defined in `frontend/src/App.tsx`.

## Main Pages

### `UploadPage`

- landing page
- manages file selection state
- validates the upload mix
- starts jobs through `/api/jobs`

### `JobProgressPage`

- polls job status
- subscribes to `/api/jobs/{jobId}/events`
- renders progress bar, phase timeline, live log panel, and cancel action

### `ResultsPage`

- fetches either `/api/jobs/{jobId}/result` or `/api/scans/{scanId}`
- renders summary cards
- renders severity rollups
- shows artifact accordions with tabs
- supports export links
- shows dependency-tree text when present
- reuses the same dashboard UI for fresh and reopened persisted scans

### `ScanHistoryPage`

- loads persisted scan summaries from `/api/scans`
- supports local search, filtering, and sorting
- links to reopened persisted results
- supports delete flow with confirmation
- supports simple notes and tags editing
- exposes export links for persisted scans through their stored `jobId`

### `SettingsPage`

- loads masked NVD API key status from `/api/settings/nvd`
- saves and removes the local NVD API key
- triggers best-effort NVD key validation
- shows Dependency-Check DB status details
- starts manual DB sync
- subscribes to `/api/vulnerability-db/events` for live sync logs

## Shared Components

Visible app-level shared components:

- `AppShell`
- `UploadZone`
- `theme-provider`
- `theme-toggle`

`AppShell` currently owns:

- page chrome
- app header
- top-level navigation for upload, scan history, and settings
- DB status indicator
- DB sync button
- routed content outlet

## UI Component System

Current UI kit is lightweight and local to the repo.

Visible components under `src/components/ui`:

- `accordion`
- `badge`
- `button`
- `card`
- `progress`
- `tabs`
- `tooltip`

This is effectively a shadcn-style component layer built from Radix primitives plus local styling helpers.

## Theme Handling

Theme handling uses:

- `next-themes`
- `ThemeProvider`
- `ThemeToggle`
- CSS custom properties in `src/index.css`

Supported modes:

- light
- dark
- system

Theme preference is intended to persist locally in browser storage.

## API Client Structure

Current API helpers live in:

- `src/lib/api.ts`

Visible request helpers include:

- `createAnalysisJob`
- `fetchJobStatus`
- `fetchJobResult`
- `fetchScans`
- `fetchStoredScan`
- `deleteStoredScan`
- `updateStoredScan`
- `cancelJob`
- `fetchNvdSettings`
- `saveNvdSettings`
- `testNvdSettings`
- `deleteNvdSettings`
- `fetchVulnerabilityDbStatus`
- `syncVulnerabilityDb`

Current shared frontend typing lives in:

- `src/lib/types.ts`

## SSE Handling

Current SSE behavior is page-local rather than abstracted into a dedicated hook.

Visible behavior:

- `JobProgressPage` opens `EventSource` against `/api/jobs/{jobId}/events`
- listens for `STARTED`, `PROGRESS`, `LOG`, `WARNING`, `ERROR`, `COMPLETED`, `CANCELLED`
- appends incoming events into local component state

There is currently no reusable SSE hook or centralized event-state store.

The new settings page also uses page-local SSE for vulnerability DB sync events.

## Results Dashboard Structure

Current results page structure:

- top summary cards
- severity summary cards
- filters for text query and severity
- artifact accordion list
- per-artifact tabs:
  - overview
  - manifest
  - dependencies
  - vulnerabilities
  - raw metadata
- optional dependency-tree block
- export actions for JSON, Markdown, and HTML

The reusable results dashboard now sits underneath both:

- fresh scan result flow
- reopened persisted scan history flow

## Planned v2 UI Pages

Future sessions are expected to add pages or major UI surfaces for:

- Dependency-Check DB status and manual sync management
- scan comparison
- dependency tree visualization
- path-to-dependency explorer
- version conflict / convergence analysis
- unused dependency analysis and dependency slimming advice
- suppressions and policy management
- SBOM import/export workflows

When adding those screens, future sessions should reuse the current `AppShell`, `src/lib/types.ts`, and shared UI components where practical rather than building a second design system.
