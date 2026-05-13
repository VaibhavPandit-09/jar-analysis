# Backend Structure

## Package Layout

Current package layout under `backend/src/main/java/com/jarscan`:

- `config`
- `controller`
- `dto`
- `job`
- `maven`
- `model`
- `persistence`
- `service`
- `util`
- `vulnerability`

## Controller Responsibilities

### `AppInfoController`

- lightweight application info endpoint
- exposes selected configuration values

### `JobController`

- create analysis jobs
- stream per-job SSE events
- fetch job status
- fetch completed job result
- export job result
- cancel running jobs

### `ScanHistoryController`

- list persisted scans
- fetch stored scan details
- delete stored scans
- update stored notes and tags

### `VulnerabilityDbController`

- expose Dependency-Check DB status
- trigger manual database sync
- stream DB sync SSE events

### `SettingsController`

- expose masked NVD API key settings status
- save a new local NVD API key
- run best-effort key validation
- remove the locally stored NVD API key

### `SpaForwardController`

- forwards SPA routes for deep-link refresh support

### `ScanComparisonController`

- compare two persisted scans via `GET /api/compare?base={scanId}&target={scanId}`
- delegates comparison logic to service layer

## Service Responsibilities

### `AnalysisJobService`

- owns the active in-memory job registry
- validates uploads
- creates per-job workspaces
- decides archive versus `pom.xml` flow
- now also handles project ZIP extraction and structure detection flow
- orchestrates Maven resolution, dependency-tree attachment, artifact analysis, vulnerability scanning, and final result creation
- publishes SSE progress events
- handles cancellation
- now persists terminal scan state through `ScanHistoryService`
- still serves job status and results from memory first for active or recent jobs

### `ScanHistoryService`

- maps completed or terminal jobs into persisted scan records
- extracts summary fields from `AnalysisResult`
- stores full `result_json`
- looks up persisted result and status by `jobId`
- supports history list, detail, delete, and metadata update operations

### `ScanComparisonService`

- loads baseline and target persisted scans
- compares flattened artifacts/dependencies using coordinate-first identity (`groupId:artifactId`) with file/hash fallback keys
- detects dependency additions, removals, version changes, coordinate changes, Java version changes, and vulnerability-count changes
- compares vulnerability deltas (`NEW`, `FIXED`, `CHANGED`, `UNCHANGED`) using CVE+artifact identity with fallback ID logic
- returns before/after summary count diffs plus warnings for partial/malformed persisted results

### `JarAnalyzerService`

- inspects archive files
- computes hashes
- counts entries
- detects fat JAR patterns
- reads class major versions
- parses manifest metadata
- extracts Maven coordinates
- detects module metadata
- recursively analyzes nested archives
- now records packaging inspection metadata for fat JAR, WAR, and EAR layouts

### `ProjectArchiveService`

- safely extracts uploaded project ZIP files into job-local workspaces
- enforces zip-slip protection
- enforces extracted-size and file-count limits

### `ProjectStructureDetector`

- scans extracted project ZIP contents
- finds `pom.xml` files and selects a best-effort root POM
- detects packaged archives, compiled class directories, dependency library directories, Spring metadata, ServiceLoader metadata, and resource files
- derives a project structure summary for result JSON and UI rendering

### `MavenResolutionService`

- builds Maven CLI commands
- executes Maven with `ProcessBuilder`
- copies resolved dependencies into a job-local directory
- prefers machine-readable Maven dependency-tree capture when available
- falls back to parsing text-tree output when JSON output is unavailable
- attaches parsed dependency-tree nodes plus raw text fallback into the resolution result
- streams Maven log output upward

### `DependencyPathService`

- resolves one or more root-to-node paths for a dependency key (`groupId:artifactId`)
- supports path-to-dependency UI and future backend drill-down use cases

### `DependencyConflictAnalysisService`

- analyzes the parsed Maven dependency tree for multi-version coordinates
- derives version conflict findings, convergence findings, selected versions, requested versions, and introducing paths
- emits copyable `dependencyManagement` snippets without modifying uploaded POMs

### `DuplicateClassAnalysisService`

- scans analyzed dependency archives for duplicate `.class` entries
- detects split packages and selected high-signal collision patterns such as multiple SLF4J bindings
- enforces configurable scan limits to avoid excessive work on very large archive sets

### `LicenseAnalysisService`

- extracts best-effort dependency license evidence from embedded Maven `pom.xml`, manifest fields, and license/notice files
- classifies common licenses into permissive, weak copyleft, strong copyleft, commercial, unknown, or multiple
- surfaces confidence and warnings rather than pretending license evidence is always complete

### `ProgressEventService`

- stores SSE emitters per job
- replays prior events to new subscribers
- publishes job progress, log, error, and completion events

### `ReportExportService`

- serializes JSON export
- renders Markdown export
- renders HTML export

### `VulnerabilityScannerService`

- abstraction-facing service layer for dependency vulnerability scanning

### `VulnerabilityDbEventService`

- SSE fanout for database sync events

### `NvdSettingsService`

- validates NVD API key format
- stores the raw key through a local secret file service
- exposes masked status only
- never returns the raw key to controllers

### `NvdApiKeyStore`

- reads and writes the raw NVD API key at `/app/data/secrets/nvd-api-key`
- uses best-effort restricted file permissions

### `VulnerabilityDbStatusStore`

- stores last sync metadata in SQLite app settings
- tracks last sync start, completion, duration, status, and last error

## Persistence Layer

### `ScanHistoryRepository`

- plain JDBC repository backed by SQLite
- handles upsert, list, lookup, delete, and metadata update operations
- stores tags as JSON text
- deserializes `result_json` back into `AnalysisResult`

### Persistence Models

- `PersistedScanRecord`
- `PersistedScanUpsert`
- `ScanSearchCriteria`
- `StoredScanMetadataUpdate`
- `AppSettingRecord`

### Additional Persistence Repositories

- `AppSettingsRepository`
  - stores general application setting metadata in SQLite
  - currently used for NVD key metadata and vulnerability DB sync state

These exist to keep controller DTOs and database row mapping separated from job orchestration models.

## Model / DTO Responsibilities

Visible DTO and model roles:

- `AnalysisResult`: completed job payload
- `DependencyTree`: structured Maven dependency tree payload
- `DependencyTreeNode`: individual dependency tree node with path metadata
- `AnalysisSummary`: top-level totals and severity summary
- `VersionConflictFinding`: per-coordinate version conflict finding
- `ConvergenceFinding`: dependency convergence issue with selected version and snippet
- `DuplicateClassFinding`: duplicate class, split package, or pattern collision finding
- `LicenseFinding`: dependency license evidence and classification
- `ArtifactAnalysis`: per-artifact result including nested artifacts
- `DependencyInfo`: dependency rows shown in UI
- `VulnerabilityFinding`: vulnerability details
- `ProgressEvent`: unified SSE event payload
- `AnalysisJobResponse`: job creation response
- `AnalysisJobStatusResponse`: polling status response
- `StoredScanSummaryResponse`: history list row payload
- `StoredScanResponse`: stored scan detail payload
- `UpdateStoredScanRequest`: notes and tags patch payload
- `VulnerabilityDbStatus`: Dependency-Check DB state
- `NvdSettingsStatusResponse`: masked NVD configuration status
- `NvdSettingsTestResponse`: best-effort validation response
- `MavenCoordinates`: extracted group, artifact, version
- `ManifestInfo`: manifest fields and raw attributes
- `JavaVersionInfo`: bytecode version summary
- `AnalysisJob`: mutable active job state

## Session 7 Dependency Tree Notes

- Maven tree parsing is intentionally resilient: JSON output is preferred, but text parsing remains the compatibility fallback.
- Parsed dependency trees are persisted inside `result_json`, so reopened scans can render the same dependency-path UI without rerunning Maven.
- `pathFromRoot` is stored per node to keep the path-to-dependency experience simple and deterministic in the frontend.

## Session 8 Dependency Intelligence Notes

- conflict and convergence analysis intentionally build on the Session 7 parsed dependency tree rather than shelling out to extra Maven rules
- duplicate class scanning is bounded by `JarScanProperties.maxDuplicateClassScanJars` and additional in-service entry limits to keep scans local-first and predictable
- license extraction is explicitly best-effort and should surface warnings or unknown categories when evidence is weak

## Configuration Layer

Visible configuration:

- `JarScanProperties`
- `DatabaseConfig`
- `AsyncConfig`
- `application.yml`

Important backend configuration concerns already represented:

- app version
- data directory
- SQLite DB path
- Dependency-Check command path
- nested archive depth
- extraction budget
- Maven timeout
- default Maven dependency scope
- duplicate-class scan jar limit
- multipart upload size
- local secret file path derived from `dataDir`

## Job System

The job system is intentionally split between transient execution state and persisted history.

Current characteristics:

- jobs keyed by generated UUID
- asynchronous execution via shared executor
- temporary workspace per job
- result retained in memory while the app is running
- completed, failed, and cancelled terminal states persisted to SQLite
- `GET /api/jobs/{jobId}/result` and `/status` can fall back to persisted data when the in-memory job is gone
- cancellation tries to stop active external processes

## Analyzer System

The analyzer system remains archive-centric.

It analyzes:

- top-level uploads
- nested JAR and WAR libraries in common bundle directories
- bytecode headers
- manifest attributes
- Maven metadata files

It does not execute uploaded code.

## Maven System

Current Maven flow uses the CLI directly rather than an embedded resolver library.

Important code path:

- `AnalysisJobService` detects `InputType.POM`
- `MavenResolutionService` builds CLI commands
- dependency files are copied into a job-local output directory
- dependency tree text is captured for output and export

## Vulnerability System

Visible vulnerability package contents:

- `VulnerabilityScanner` interface
- `DependencyCheckVulnerabilityScanner`
- `NoOpVulnerabilityScanner`

Current behavior:

- primary scanning uses local Dependency-Check CLI
- JSON output is parsed back into `VulnerabilityFinding` objects
- findings are attached to artifacts and nested artifacts
- DB sync is local and optional
- a configured NVD API key is appended to the Dependency-Check command through a dedicated command builder
- masked command text is safe to stream, but the raw key is never published

## Current Persistence Limitation

Persistence is now available for completed scan history, but the backend is not fully persistence-backed yet.

Current gaps:

- active jobs are still in-memory only
- SSE event buffers are still in-memory only
- suppressions and policies are not persisted yet

## Planned v2 Persistence Direction

Planned next steps on top of Session 2:

- SQLite-backed app settings
- scan history reopening in the UI
- comparison features built against persisted scan summaries plus full stored results
- locally persisted suppressions and policies in later sessions
