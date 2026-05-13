# Backend Structure

## Controllers

Primary controllers:
- `JobController`
  - create jobs
  - subscribe to SSE progress
  - read job status and result
  - export job results
- `ScanHistoryController`
  - list stored scans
  - reopen stored scans
  - export stored scans
  - get stored scan by job id
  - update notes/tags
  - delete scans
  - compare scans
- `SettingsController`
  - NVD API key save/test/delete/status
- `VulnerabilityDbController`
  - DB status and manual sync
- `SuppressionController`
  - CRUD for suppressions
- `PolicyController`
  - CRUD for policies
  - evaluate policies for a stored scan
- `SbomController`
  - CycloneDX JSON import
- `SpaForwardController`
  - forwards SPA routes to `index.html`

## Services

Key services:
- `AnalysisJobService`
  - orchestrates upload execution and result generation
- `JarAnalyzerService`
  - archive and nested archive inspection
- `MavenResolutionService`
  - Maven dependency copy, tree capture, and dependency analyze capture
- `ProjectArchiveService`
  - safe ZIP extraction
- `ProjectStructureDetector`
  - root POM and compiled/resource/dependency directory detection
- `VulnerabilityScannerService`
  - delegates to Dependency-Check-backed scanning
- `DependencyConflictAnalysisService`
  - version conflicts and convergence
- `DuplicateClassAnalysisService`
  - duplicate classes, split packages, pattern collisions
- `LicenseAnalysisService`
  - best-effort license extraction and classification
- `DependencyUsageAnalysisService`
  - evidence-based usage findings with confidence
- `DependencySlimmingAdvisorService`
  - slimming opportunities and AWS bundle advice
- `PolicyService`
  - built-in policy seeding, policy CRUD, policy evaluation
- `SuppressionService`
  - suppression CRUD and finding matching
- `ScanResultViewService`
  - result decoration pipeline for suppressions + policy reevaluation
- `SbomService`
  - CycloneDX JSON import/export
- `ScanHistoryService`
  - persistence of scans and reopen behavior
- `ScanComparisonService`
  - comparison logic over stored results

## Persistence

Repositories under `persistence/` manage:
- `scans`
- `app_settings`
- `suppressions`
- `policies`

SQLite is accessed through `NamedParameterJdbcTemplate`.
Flyway manages schema migrations.

## DTO Highlights

`AnalysisResult` is the central payload and now includes:
- artifacts
- dependency tree
- version conflicts and convergence findings
- duplicate class findings
- license findings
- dependency usage findings
- slimming opportunities
- AWS bundle advice
- policy evaluation
- warnings and errors
- optional project structure summary

Finding DTOs that support suppressions include suppression metadata fields rather than deleting raw findings.
