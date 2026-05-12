# Architecture

## System Shape

JARScan is a full-stack local web application composed of:

- a Spring Boot backend
- a React/Vite frontend
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
- `service`
- `util`
- `vulnerability`

Important controllers currently visible:

- `AppInfoController`
- `JobController`
- `VulnerabilityDbController`
- `SpaForwardController`

Important services currently visible:

- `AnalysisJobService`
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
- backend build stage with Maven + Eclipse Temurin 25
- final runtime image based on `maven:3.9.14-eclipse-temurin-25`

The final runtime includes:

- Java 25 runtime
- Maven CLI
- the Spring Boot application JAR
- built frontend static files inside the application
- OWASP Dependency-Check CLI installed under `/opt/dependency-check`

## Data Persistence As Of v1

Current persistence in v1 is limited:

- completed scan jobs/results live only in backend memory for the lifetime of the running container
- Maven repository cache persists through Docker volume `jarscan-m2`
- Dependency-Check data persists through Docker volume `jarscan-data`

Current mounted locations:

- `/root/.m2`
- `/app/data`
- `/app/data/dependency-check`

## Planned SQLite Persistence For v2

Planned v2 persistence direction is SQLite at:

- `/app/data/jarscan.db`

The intent is to persist:

- scan metadata and summary fields
- full result JSON
- settings such as NVD API key and defaults
- suppressions
- policies

## SSE Progress Architecture

Current progress model is based on Server-Sent Events:

- job execution is started with `POST /api/jobs`
- clients subscribe to `GET /api/jobs/{jobId}/events`
- the backend stores job events in memory and replays them to new subscribers
- progress events include phase, type, message, optional percent, and item counters

There is a second SSE stream for vulnerability DB updates:

- `GET /api/vulnerability-db/events`

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
- `VulnerabilityDbController`: DB status, manual sync trigger, DB event stream
- `AnalysisJobService`: in-memory job lifecycle and orchestration
- `JarAnalyzerService`: archive inspection and nested archive analysis
- `MavenResolutionService`: Maven CLI orchestration
- `ReportExportService`: JSON/Markdown/HTML export generation

Frontend pieces visible from code:

- `AppShell`
- `UploadZone`
- `UploadPage`
- `JobProgressPage`
- `ResultsPage`
- `theme-provider`
- `theme-toggle`

## Important Data Models Visible In Code

Key DTOs/models currently visible:

- `AnalysisResult`
- `AnalysisSummary`
- `ArtifactAnalysis`
- `DependencyInfo`
- `VulnerabilityFinding`
- `ProgressEvent`
- `AnalysisJob`
- `VulnerabilityDbStatus`
- `MavenCoordinates`
- `ManifestInfo`
- `JavaVersionInfo`

These are the current backbone of scan execution, response payloads, exports, and UI rendering.
