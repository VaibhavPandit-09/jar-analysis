# Design Decisions

## Local-First Over SaaS-First

JARScan intentionally runs locally with Docker-friendly persistence and local CLI integrations. This reduces data egress concerns for internal artifacts and gives users predictable control over caches and secrets.

## Preserve Raw Results, Decorate On Read

Completed scans are persisted as raw `AnalysisResult` JSON plus relational summary columns.

Suppressions and policy evaluation are applied when results are reopened rather than mutating raw findings in place.

Why:
- auditability
- easier policy evolution
- easier suppression expiry behavior
- fewer destructive persistence changes

## Evidence-Based Usage, Not Absolute Claims

Unused-dependency reporting is intentionally phrased with statuses and confidence rather than certainty.

Why:
- Java runtime behavior is often indirect
- reflection, ServiceLoader, Spring auto-configuration, logging, JDBC drivers, and container/plugin wiring can hide runtime usage

## JSON-First Maven Tree, Text Fallback

Maven dependency tree capture prefers JSON output when the available dependency plugin supports it, but text parsing remains the robust fallback.

Why:
- installed plugin versions vary
- the app should not become brittle when JSON output is unavailable

## SBOM Import As Scan-Like Results

CycloneDX JSON imports are converted into a scan-like `AnalysisResult` rather than stored in a totally separate object model.

Why:
- reuse existing history, compare, export, and policy flows
- reduce frontend branching
- keep import/export features consistent

## SQLite Plus Result JSON

The app keeps summary columns for fast list behavior and full result JSON for reopen/export fidelity.

Why:
- fast history screens
- additive model evolution
- low schema churn for nested structures
