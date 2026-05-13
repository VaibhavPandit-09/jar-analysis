# Architecture

## High-Level Shape

JARScan is a Spring Boot backend plus a React/Vite frontend served together behind the backend at port `8080` in Docker.

Major layers:
- frontend SPA for upload, results, history, settings, suppressions, and policies
- backend REST APIs and SSE streams
- SQLite persistence through JDBC and Flyway
- Maven CLI and Dependency-Check CLI integration
- local filesystem workspaces for scan execution

## Result Model Strategy

The backend persists two views of a completed scan:
- relational summary columns in `scans`
- full `AnalysisResult` JSON in `result_json`

This allows:
- fast list/search/sort/filter in history
- exact reopen/export of prior results
- additive schema evolution without fully normalizing every nested artifact and finding

## Read-Time Decoration

Raw persisted results are preserved.

When a result is reopened, JARScan decorates it with:
1. active suppressions for vulnerabilities, licenses, usage findings, version conflicts, duplicate classes, and policy findings
2. current policy evaluation based on the latest enabled policy definitions
3. refreshed policy summary counts and overall status

This keeps persistence honest while allowing policy and suppression behavior to evolve independently.

## Execution Flow

1. Upload handling determines input type.
2. A job workspace is created.
3. For project ZIPs, safe extraction and structure detection run.
4. For Maven-backed inputs, Maven resolution runs:
   - copy-dependencies
   - dependency tree capture
   - dependency analyze capture
5. Archive analysis runs recursively without executing uploaded code.
6. Dependency-Check scans analyzed artifacts.
7. Higher-level analyzers produce:
   - dependency tree
   - conflicts and convergence
   - duplicate classes
   - licenses
   - usage analysis
   - slimming opportunities
   - AWS bundle advice
8. Policy evaluation runs.
9. The result is persisted and available in history.

## SBOM Flow

- export uses the current `AnalysisResult` to produce CycloneDX JSON
- import accepts CycloneDX JSON, converts components into a scan-like `AnalysisResult`, persists it, and exposes it through history/compare/export/policy flows
