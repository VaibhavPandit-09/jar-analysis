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
  - supported upload modes:
    - one or more `.jar` / `.war` / `.ear` archives
    - one `pom.xml`
    - one `.zip` project archive

- `GET /api/jobs/{jobId}/status`
  - returns current job state
  - checks active in-memory jobs first
  - falls back to persisted status for stored completed or failed scans when possible

- `GET /api/jobs/{jobId}/result`
  - returns completed result JSON
  - checks active in-memory result first
  - falls back to persisted `result_json` from SQLite when the job is no longer in memory
  - when Maven tree output exists, the payload now includes:
    - `dependencyTree`
      - structured root/child nodes
      - node fields include `id`, `groupId`, `artifactId`, `type`, `classifier`, `version`, `scope`, `depth`, `parentId`, `children`, `direct`, `transitive`, `omitted`, `omittedReason`, `conflict`, `rawLine`, and `pathFromRoot`
    - `versionConflicts`
      - list of `VersionConflictFinding`
      - fields include `groupId`, `artifactId`, `resolvedVersion`, `requestedVersions`, `pathsByVersion`, `conflictType`, `riskLevel`, `recommendation`, and `dependencyManagementSnippet`
    - `convergenceFindings`
      - list of `ConvergenceFinding`
      - fields include `groupId`, `artifactId`, `versionsFound`, `pathsByVersion`, `selectedVersion`, `recommendation`, and `snippet`
    - `duplicateClasses`
      - list of `DuplicateClassFinding`
      - fields include `findingType`, `symbol`, `packageName`, `artifacts`, `severity`, `recommendation`, and `shadowingWarning`
    - `licenses`
      - list of `LicenseFinding`
      - fields include `groupId`, `artifactId`, `version`, `licenseName`, `licenseUrl`, `source`, `confidence`, `category`, and `warnings`
    - `dependencyTreeText`
      - raw Maven tree text when text output was used or retained for debugging

- `POST /api/jobs/{jobId}/cancel`
  - requests cancellation of an active job

### Job SSE

- `GET /api/jobs/{jobId}/events`
  - job progress, log, completion, and error event stream for active jobs

### Export Endpoints

- `GET /api/jobs/{jobId}/export?format=json`
- `GET /api/jobs/{jobId}/export?format=markdown`
- `GET /api/jobs/{jobId}/export?format=html`

Exports currently operate against the existing job/result model. The Session 3 history UI reuses these endpoints through each persisted scan's stored `jobId`, because `GET /api/jobs/{jobId}/result` already falls back to persisted SQLite results when needed.

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
  - used by the reopened scan results route in the frontend
  - persisted result JSON now preserves parsed `dependencyTree` data when Maven tree output was available during the original scan

- `DELETE /api/scans/{scanId}`
  - deletes a stored scan record

- `PATCH /api/scans/{scanId}`
  - updates editable metadata
  - current editable fields:
    - `notes`
    - `tags`

### Scan Comparison API

- `GET /api/compare?base={scanId}&target={scanId}`
  - compares two persisted scans
  - response includes:
    - baseline scan summary
    - target scan summary
    - summary before/after diffs
    - dependency changes (`ADDED`/`REMOVED`/`UPDATED`/`UNCHANGED`)
    - vulnerability changes (`NEW`/`FIXED`/`CHANGED`/`UNCHANGED`)
    - warnings/errors for partial comparison scenarios

## Current Frontend Route Surface That Depends On These APIs

- `/`
  - upload page
- `/scan-history`
  - persisted scan history list and actions
- `/settings`
  - NVD API key settings, Dependency-Check DB status, and manual sync UI
- `/jobs/{jobId}`
  - live progress page
- `/jobs/{jobId}/results`
  - fresh scan results page
- `/scans/{scanId}/results`
  - reopened persisted results page

### NVD Settings APIs

- `GET /api/settings/nvd`
  - returns configured state, masked key suffix, storage mode, and updated timestamp

- `POST /api/settings/nvd`
  - saves a new NVD API key
  - request body:
    - `apiKey`
  - does not return the raw key after save

- `POST /api/settings/nvd/test`
  - performs local, best-effort validation of the stored key format

- `DELETE /api/settings/nvd`
  - removes the locally stored NVD API key

### Vulnerability DB APIs

- `GET /api/vulnerability-db/status`
  - current Dependency-Check DB status
  - includes CLI version, data directory, API key configured flag, last sync metadata, and last error if present

- `POST /api/vulnerability-db/sync`
  - starts local DB sync
  - returns HTTP 409 if a sync is already running

### Vulnerability DB SSE

- `GET /api/vulnerability-db/events`
  - Dependency-Check DB sync event stream

## Current Response Types Visible In Code

The following response DTOs are visible or directly implied by controller/service code:

- `AnalysisJobResponse`
- `AnalysisJobStatusResponse`
- `AnalysisResult`
- `DependencyTree`
- `DependencyTreeNode`
- `VersionConflictFinding`
- `ConvergenceFinding`
- `DuplicateClassFinding`
- `LicenseFinding`
- `NvdSettingsStatusResponse`
- `NvdSettingsTestResponse`
- `ProgressEvent`
- `VulnerabilityDbStatus`
- `StoredScanSummaryResponse`
- `StoredScanResponse`
- `ProjectStructureSummary`
- `PackagingInspection`

## Session 5 Comparison Status

Implemented endpoint:

- `GET /api/compare?base={scanId}&target={scanId}`

## Session 7 Dependency Tree Status

Implemented in result payloads:

- Maven dependency tree parsing with JSON-first capture and text fallback
- `pathFromRoot` per dependency node so the frontend can explain why a dependency is present without an extra API call

## Session 8 Dependency Intelligence Status

Implemented in result payloads:

- version conflict analysis based on the parsed Maven dependency tree
- dependency convergence findings with suggested `dependencyManagement` snippets
- duplicate class and split-package findings across analyzed dependency archives
- best-effort license extraction and classification from embedded POM metadata, manifest fields, and license files

## Planned v2 Settings Endpoints

Expected direction for later sessions:

- `GET /api/settings`
- `PUT /api/settings`

Potential stored values:

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
