# API Reference

This file documents currently visible v1 backend endpoints and planned v2 API expansion.

## Existing v1 Endpoints

### App Info

- `GET /api/app/info`
  - lightweight application info payload

### Job APIs

- `POST /api/jobs`
  - multipart upload entrypoint
  - accepts `files`

- `GET /api/jobs/{jobId}/status`
  - returns current job state

- `GET /api/jobs/{jobId}/result`
  - returns completed result JSON
  - currently returns HTTP 202-style error behavior if not ready yet

- `POST /api/jobs/{jobId}/cancel`
  - requests cancellation

### Job SSE

- `GET /api/jobs/{jobId}/events`
  - job progress/log/completion event stream

### Export Endpoints

- `GET /api/jobs/{jobId}/export?format=json`
- `GET /api/jobs/{jobId}/export?format=markdown`
- `GET /api/jobs/{jobId}/export?format=html`

### Vulnerability DB APIs

- `GET /api/vulnerability-db/status`
  - current Dependency-Check DB status

- `POST /api/vulnerability-db/sync`
  - starts local DB sync

### Vulnerability DB SSE

- `GET /api/vulnerability-db/events`
  - DB sync event stream

## Current v1 Response Types Visible In Code

The following response DTOs are visible:

- `AnalysisJobResponse`
- `AnalysisJobStatusResponse`
- `AnalysisResult`
- `ProgressEvent`
- `VulnerabilityDbStatus`

## Planned v2 Scan History Endpoints

Expected direction for v2:

- `GET /api/scans`
- `GET /api/scans/{scanId}`
- `GET /api/scans/{scanId}/result`
- `DELETE /api/scans/{scanId}`
- `POST /api/scans/{scanId}/reopen`

Possible query features:

- search
- sort
- pagination
- filters by input type, severity, date range

## Planned v2 Comparison Endpoints

Expected direction:

- `GET /api/scans/compare?left={scanId}&right={scanId}`
- or `POST /api/scans/compare`

Possible outputs:

- dependency additions/removals
- version changes
- vulnerability deltas
- policy delta summaries

## Planned v2 Settings / NVD Endpoints

Expected direction:

- `GET /api/settings`
- `PUT /api/settings`
- `GET /api/settings/nvd`
- `PUT /api/settings/nvd`

Potential stored values:

- NVD API key
- default Maven dependency scope
- scan retention preferences

## Planned v2 Suppression Endpoints

Expected direction:

- `GET /api/suppressions`
- `POST /api/suppressions`
- `PUT /api/suppressions/{suppressionId}`
- `DELETE /api/suppressions/{suppressionId}`

## Planned v2 Policy Endpoints

Expected direction:

- `GET /api/policies`
- `POST /api/policies`
- `PUT /api/policies/{policyId}`
- `DELETE /api/policies/{policyId}`
- `POST /api/policies/evaluate`

## Planned v2 SBOM Endpoints

Expected direction:

- `POST /api/sbom/import`
- `GET /api/scans/{scanId}/sbom`
- `GET /api/scans/{scanId}/sbom?format=cyclonedx`
- `GET /api/scans/{scanId}/sbom?format=spdx`

These are planning notes, not currently implemented endpoints.
