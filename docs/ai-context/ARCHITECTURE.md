# Architecture

## System Shape

JARScan is a full-stack local web application composed of:

- a Spring Boot backend
- a React and Vite frontend
- a Docker runtime that bundles Java 25, Maven CLI, and Dependency-Check CLI

The frontend is built into static assets and served by the backend in the containerized deployment.

## Backend Structure

The backend root package is `com.jarscan`.

Visible package areas:

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

Important controllers currently visible:

- `AppInfoController`
- `JobController`
- `ScanHistoryController`
- `VulnerabilityDbController`
- `SpaForwardController`

Important services currently visible:

- `AnalysisJobService`
- `ScanHistoryService`
- `JarAnalyzerService`
- `MavenResolutionService`
- `ProgressEventService`
- `ReportExportService`
- `VulnerabilityDbEventService`
- `VulnerabilityScannerService`

## Frontend Structure

The frontend is a Vite React app with a small route tree.

Visible major areas:

- `src/pages`
- `src/components`
- `src/components/ui`
- `src/lib`

Current route surface:

- `/`
- `/jobs/:jobId`
- `/jobs/:jobId/results`

The app shell provides:

- header
- theme controls
- vulnerability DB status indicator
- sync button
- routed page outlet

## Docker Structure

Current Docker setup uses:

- frontend build stage with Node
- backend build stage with Maven plus Eclipse Temurin 25
- final runtime image based on `maven:3.9.14-eclipse-temurin-25`

The final runtime includes:

- Java 25 runtime
- Maven CLI
- the Spring Boot application JAR
- built frontend static files inside the application
- OWASP Dependency-Check CLI installed under `/opt/dependency-check`

## Data Persistence As Of Session 2

Persistence is now split into three categories.

Persistent local data:

- SQLite scan history database under `/app/data/jarscan.db` by default
- Maven repository cache under `/root/.m2`
- Dependency-Check data under `/app/data/dependency-check`

Transient runtime state:

- active jobs in the in-memory registry
- live SSE emitters and replay buffers
- temporary job workspaces used during analysis

## Planned SQLite Persistence For v2

The Session 2 persistence foundation intentionally starts with a single history table.

Currently persisted:

- scan metadata
- summary counts
- job status for terminal states
- full result JSON
- editable notes and tags

Planned future SQLite data:

- app settings such as NVD API key and defaults
- suppressions
- policies

## SSE Progress Architecture

Current progress model is based on Server-Sent Events.

Job flow:

- job execution starts with `POST /api/jobs`
- clients subscribe to `GET /api/jobs/{jobId}/events`
- backend stores active job events in memory and replays them to new subscribers
- progress events include phase, type, message, optional percent, and item counters

Vulnerability DB flow:

- `GET /api/vulnerability-db/events` streams Dependency-Check DB update events

Important Session 2 note:

- persisted scan history does not replace the live SSE model
- SSE is still runtime-only for active work, while final scan results are saved to SQLite after terminal completion

## Maven Execution Flow

Current Maven flow for `pom.xml` uploads:

1. upload is written into a temporary per-job workspace
2. backend detects input type `POM`
3. `MavenResolutionService` shells out using `ProcessBuilder`
4. Maven runs `dependency:copy-dependencies`
5. Maven runs `dependency:tree`
6. resolved archives are analyzed as normal artifacts
7. Maven stdout is streamed into job log events

Important design constraint:

- Maven CLI is the resolver of record; the app does not manually implement transitive dependency resolution

## Dependency-Check Integration Flow

Current vulnerability flow:

1. backend gathers artifact file paths
2. `VulnerabilityScannerService` delegates to a `VulnerabilityScanner`
3. primary implementation is `DependencyCheckVulnerabilityScanner`
4. Dependency-Check CLI is executed locally
5. JSON output is parsed
6. findings are attached back onto analyzed artifacts
7. DB sync status and update events are exposed through `VulnerabilityDbController`

Fallback behavior exists through:

- `NoOpVulnerabilityScanner`

## Main Services / Controllers / Components

Backend pieces visible from code:

- `JobController`: job creation, status, result, SSE stream, cancellation, exports
- `ScanHistoryController`: scan history list, detail, delete, metadata patch
- `VulnerabilityDbController`: DB status, manual sync trigger, DB event stream
- `AnalysisJobService`: active job lifecycle and orchestration
- `ScanHistoryService`: persistence bridge between terminal jobs and SQLite history
- `JarAnalyzerService`: archive inspection and nested archive analysis
- `MavenResolutionService`: Maven CLI orchestration
- `ReportExportService`: JSON, Markdown, and HTML export generation

Frontend pieces visible from code:

- `AppShell`
- `UploadZone`
- `UploadPage`
- `JobProgressPage`
- `ResultsPage`
- `theme-provider`
- `theme-toggle`

## Important Data Models Visible In Code

Key DTOs and models currently visible:

- `AnalysisResult`
- `AnalysisSummary`
- `ArtifactAnalysis`
- `DependencyInfo`
- `VulnerabilityFinding`
- `ProgressEvent`
- `AnalysisJob`
- `StoredScanSummaryResponse`
- `StoredScanResponse`
- `VulnerabilityDbStatus`
- `MavenCoordinates`
- `ManifestInfo`
- `JavaVersionInfo`

These are the current backbone of scan execution, response payloads, persistence mapping, exports, and UI rendering.
