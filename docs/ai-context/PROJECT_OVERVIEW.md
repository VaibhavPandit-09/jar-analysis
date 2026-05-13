# Project Overview

## Product Definition

JARScan is a local-first Java dependency intelligence tool.

It focuses on:
- archive and dependency inspection
- vulnerability surfacing through Dependency-Check
- dependency graph reconstruction when Maven evidence is available
- dependency usage analysis
- license review
- duplicate class and version conflict analysis
- slimming and bundle-reduction guidance
- suppressions and policy evaluation
- SBOM import/export

It does not perform application source-code vulnerability scanning.

## Supported Inputs

- JAR, WAR, EAR uploads
- `pom.xml`
- project ZIP uploads
- CycloneDX JSON SBOM imports

## Core User Journeys

1. Upload or import an artifact/build/SBOM.
2. Inspect overview metrics, vulnerabilities, dependency tree, usage, licenses, conflicts, duplicate classes, and slimming opportunities.
3. Suppress accepted findings with reason and optional expiry.
4. Reopen results from scan history.
5. Compare two stored scans.
6. Export JSON, Markdown, HTML, or CycloneDX JSON.
7. Evaluate the scan against built-in or edited policies.

## Final v2 Outcome

v2 is complete and includes:
- persistent scan history
- settings-backed NVD API key handling
- Dependency-Check DB status and sync UI
- project ZIP extraction and structure detection
- dependency tree parsing and visualization
- path-to-dependency explanations
- version conflict and convergence findings
- duplicate classes and split-package findings
- license analysis
- evidence-based usage analysis
- slimming advisor and AWS bundle advisor
- suppressions
- policy engine
- CycloneDX JSON SBOM import/export
