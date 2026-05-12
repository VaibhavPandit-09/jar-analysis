# Backend Structure

## Package Layout

Current package layout under `backend/src/main/java/com/jarscan`:

- `config`
- `controller`
- `dto`
- `job`
- `maven`
- `model`
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

### `VulnerabilityDbController`

- expose Dependency-Check DB status
- trigger manual database sync
- stream DB sync SSE events

### `SpaForwardController`

- forwards SPA routes for deep-link refresh support

## Service Responsibilities

### `AnalysisJobService`

- owns the in-memory job registry
- validates uploads
- creates per-job workspaces
- decides archive vs `pom.xml` flow
- orchestrates Maven resolution, artifact analysis, vulnerability scanning, and final result creation
- publishes SSE progress events
- handles cancellation

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

### `MavenResolutionService`

- builds Maven CLI commands
- executes Maven with `ProcessBuilder`
- copies resolved dependencies into a job-local directory
- captures dependency-tree output
- streams Maven log output upward

### `ProgressEventService`

- stores SSE emitters per job
- replays prior events to new subscribers
- publishes job progress/log/error/completion events

### `ReportExportService`

- serializes JSON export
- renders simple Markdown export
- renders simple HTML export

### `VulnerabilityScannerService`

- abstraction-facing service layer for dependency vulnerability scanning

### `VulnerabilityDbEventService`

- SSE event fanout for database sync events

## Model / DTO Responsibilities

Visible DTO and model roles:

- `AnalysisResult`: completed job payload
- `AnalysisSummary`: top-level totals and severity summary
- `ArtifactAnalysis`: per artifact result including nested artifacts
- `DependencyInfo`: dependency rows shown in UI
- `VulnerabilityFinding`: vulnerability details
- `ProgressEvent`: unified SSE event payload
- `AnalysisJobResponse`: job creation response
- `AnalysisJobStatusResponse`: polling status response
- `VulnerabilityDbStatus`: Dependency-Check DB state
- `MavenCoordinates`: extracted group/artifact/version
- `ManifestInfo`: manifest fields and raw attributes
- `JavaVersionInfo`: bytecode version summary
- `AnalysisJob`: in-memory mutable job state

## Configuration Layer

Visible configuration:

- `JarScanProperties`
- `AsyncConfig`
- `application.yml`

Important backend configuration concerns already represented:

- data directory
- Dependency-Check command path
- nested-archive depth
- extraction budget
- Maven timeout
- default Maven dependency scope
- multipart upload size

## Job System

Current job system is in-memory only.

Key characteristics:

- jobs keyed by generated UUID
- asynchronous execution via shared executor
- temporary workspace per job
- result retained in memory
- status transitions through `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`
- cancellation tries to stop active external processes

## Analyzer System

The current analyzer system is archive-centric.

It analyzes:

- top-level uploads
- nested JAR/WAR libraries in common bundle directories
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
- dependency tree text is captured for output/export

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

## Current Persistence Limitation

The biggest backend limitation in v1 is persistence:

- completed scans disappear when the container restarts
- there is no SQLite or file-backed scan history yet
- settings are not persisted in application storage yet

## Planned v2 Persistence Direction

Planned v2 direction:

- SQLite database at `/app/data/jarscan.db`
- scan summaries stored in relational columns
- full result JSON stored alongside summary fields
- app settings persisted locally
- suppressions and policies stored locally

Future sessions should preserve the current DTO/result structure as much as possible so UI reuse is straightforward once persistence is added.
