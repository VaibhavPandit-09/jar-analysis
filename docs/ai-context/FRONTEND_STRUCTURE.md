# Frontend Structure

## Routes

Current primary routes:
- `/`
- `/scan-history`
- `/compare`
- `/settings`
- `/suppressions`
- `/policies`
- `/jobs/:jobId`
- `/jobs/:jobId/results`
- `/scans/:scanId/results`

## Major Pages

- `UploadPage`
  - archive upload
  - POM upload
  - project ZIP upload
  - CycloneDX JSON SBOM import
- `JobProgressPage`
  - progress bar
  - phase timeline
  - log stream
  - cancellation
- `ResultsPage`
  - resolves job-backed or stored-scan-backed results
- `ScanHistoryPage`
  - reopen, export, compare, notes/tags, delete
- `ComparePage`
  - dependency and vulnerability diffs
- `SettingsPage`
  - NVD API key and vulnerability DB status/sync
- `SuppressionsPage`
  - suppression audit and enable/pause/delete actions
- `PoliciesPage`
  - policy enable/disable and simple config editing

## Results Dashboard

The results dashboard now exposes top-level tabs for:
- artifacts
- dependency tree
- usage analysis
- slimming advisor
- version conflicts
- duplicate classes
- licenses
- policy results
- exports

Artifact sections include:
- overview
- fat JAR inspector
- manifest
- dependencies
- vulnerabilities
- raw metadata

## Suppression UX

Suppression UX pieces:
- suppress buttons in relevant result tabs
- suppression dialog with reason and optional expiry
- show/hide suppressed toggles inside panels
- manage suppressions page

## Styling Direction

The UI uses:
- consistent card-based layouts
- rounded surfaces and soft gradients
- dark-mode support
- confirmation for destructive actions
- toast feedback for copy/save/delete/sync flows
