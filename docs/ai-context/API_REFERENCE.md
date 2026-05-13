# API Reference

## Jobs

- `POST /api/jobs`
  - multipart upload of archives, `pom.xml`, or project ZIP
- `GET /api/jobs/{jobId}/events`
  - SSE progress stream
- `GET /api/jobs/{jobId}/status`
- `GET /api/jobs/{jobId}/result`
- `POST /api/jobs/{jobId}/cancel`
- `GET /api/jobs/{jobId}/export?format=json|markdown|html|cyclonedx-json`

## Stored Scans

- `GET /api/scans`
- `GET /api/scans/{scanId}`
- `PATCH /api/scans/{scanId}`
- `DELETE /api/scans/{scanId}`
- `GET /api/scans/by-job/{jobId}`
- `GET /api/scans/{scanId}/export?format=json|markdown|html|cyclonedx-json`
- `GET /api/scans/compare?base={scanId}&target={scanId}`
- `GET /api/compare?base={scanId}&target={scanId}`

## Settings And Vulnerability DB

- `GET /api/settings/nvd`
- `POST /api/settings/nvd`
- `POST /api/settings/nvd/test`
- `DELETE /api/settings/nvd`
- `GET /api/vulnerability-db/status`
- `POST /api/vulnerability-db/sync`
- `GET /api/vulnerability-db/events`

## Suppressions

- `GET /api/suppressions`
- `POST /api/suppressions`
- `PATCH /api/suppressions/{id}`
- `DELETE /api/suppressions/{id}`

Suppression payload fields:
- `type`
- `groupId`
- `artifactId`
- `version`
- `cveId`
- `reason`
- `expiresAt`
- `active`

## Policies

- `GET /api/policies`
- `POST /api/policies`
- `PATCH /api/policies/{id}`
- `DELETE /api/policies/{id}`
- `POST /api/policies/evaluate/{scanId}`

Policy payload fields:
- `id`
- `name`
- `description`
- `ruleType`
- `severity`
- `enabled`
- `config`

## SBOM

- `POST /api/sbom/import`
  - multipart field `file`
  - accepts CycloneDX JSON

## Response Notes

Reopened results include suppression metadata on supported finding types:
- `suppressed`
- `suppressionReason`
- `suppressionExpiresAt`

Results also include `policyEvaluation` with:
- `overallStatus`
- `passedCount`
- `warningCount`
- `failedCount`
- `findings[]`
