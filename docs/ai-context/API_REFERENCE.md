# API Reference

This file documents currently visible backend endpoints and planned v2 expansion.

## Existing Endpoints

### App Info

- `GET /api/app/info`
  - lightweight application info payload

### Job APIs

- `POST /api/jobs`
  - multipart upload entrypoint
  - accepts `files`

- `GET /api/jobs/{jobId}/status`
  - returns current job state
  - checks active in-memory jobs first
  - falls back to persisted status for stored completed or failed scans when possible

- `GET /api/jobs/{jobId}/result`
  - returns completed result JSON
  - checks active in-memory result first
  - falls back to persisted `result_json` from SQLite when the job is no longer in memory

- `POST /api/jobs/{jobId}/cancel`
  - requests cancellation of an active job

### Job SSE

- `GET /api/jobs/{jobId}/events`
  - job progress, log, completion, and error event stream for active jobs

### Export Endpoints

- `GET /api/jobs/{jobId}/export?format=json`
- `GET /api/jobs/{jobId}/export?format=markdown`
- `GET /api/jobs/{jobId}/export?format=html`

Exports continue to operate against the existing job/result model. Future sessions may add scan-id-based exports if needed.

### Scan History APIs

- `GET /api/scans`
  - returns stored scan summaries
  - supported query parameters:
    - `q`
    - `inputType`
    - `status`
    - `severity`
    - `sort`
    - `direction`
    - `limit`
    - `offset`

- `GET /api/scans/{scanId}`
  - returns the stored scan summary plus full persisted result

- `DELETE /api/scans/{scanId}`
  - deletes a stored scan record

- `PATCH /api/scans/{scanId}`
  - updates editable metadata
  - current editable fields:
    - `notes`
    - `tags`

### Vulnerability DB APIs

- `GET /api/vulnerability-db/status`
  - current Dependency-Check DB status

- `POST /api/vulnerability-db/sync`
  - starts local DB sync

### Vulnerability DB SSE

- `GET /api/vulnerability-db/events`
  - Dependency-Check DB sync event stream

## Current Response Types Visible In Code

The following response DTOs are visible or directly implied by controller/service code:

- `AnalysisJobResponse`
- `AnalysisJobStatusResponse`
- `AnalysisResult`
- `ProgressEvent`
- `VulnerabilityDbStatus`
- `StoredScanSummaryResponse`
- `StoredScanResponse`

## Planned v2 Comparison Endpoints

Expected direction for Session 5:

- `GET /api/scans/compare?left={scanId}&right={scanId}`
- or `POST /api/scans/compare`

Possible outputs:

- dependency additions and removals
- version changes
- vulnerability deltas
- policy delta summaries

## Planned v2 Settings / NVD Endpoints

Expected direction for Session 4:

- `GET /api/settings`
- `PUT /api/settings`
- `GET /api/settings/nvd`
- `PUT /api/settings/nvd`

Potential stored values:

- NVD API key
- default Maven dependency scope
- scan defaults

## Planned v2 Suppression Endpoints

Expected direction for Session 10:

- `GET /api/suppressions`
- `POST /api/suppressions`
- `PUT /api/suppressions/{suppressionId}`
- `DELETE /api/suppressions/{suppressionId}`

## Planned v2 Policy Endpoints

Expected direction for Session 10:

- `GET /api/policies`
- `POST /api/policies`
- `PUT /api/policies/{policyId}`
- `DELETE /api/policies/{policyId}`
- `POST /api/policies/evaluate`

## Planned v2 SBOM Endpoints

Expected direction for Session 10:

- `POST /api/sbom/import`
- `GET /api/scans/{scanId}/sbom`
- `GET /api/scans/{scanId}/sbom?format=cyclonedx`
- `GET /api/scans/{scanId}/sbom?format=spdx`

These planned endpoints are roadmap notes, not currently implemented behavior.
