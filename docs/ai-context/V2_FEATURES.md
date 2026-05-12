# V2 Features

This file documents the planned JARScan v2 feature set. These items are roadmap targets, not guarantees that every detail already exists in code.

## Persistent Scan History

- local SQLite-backed scan history
- reopen prior scan results without rescanning
- metadata-driven history list

## NVD API Key Support

- optional local configuration of NVD API key
- better Dependency-Check update throughput when configured

## Dependency-Check DB Status And Sync UI

- visible DB health/status
- last-updated timestamp
- manual sync action
- background progress feedback

## Project ZIP Support

- upload project ZIPs
- inspect Maven/Gradle project structure where practical
- derive dependency usage evidence from project contents

## WAR/EAR Intelligence

- deeper inspection of `WEB-INF/lib`, application libs, and related packaging layouts
- better bundled-dependency understanding

## Dependency Tree Visualization

- parsed Maven dependency tree
- graphical or structured hierarchical display

## Path-To-Dependency

- explain why a dependency is present
- show transitive path(s) from root dependency to target dependency

## Version Conflict Analysis

- surface competing versions in the graph
- explain which version won and where mismatches exist

## Dependency Convergence

- identify graphs that do not converge cleanly
- surface risky or inconsistent transitive version patterns

## Unused Dependency Analysis

- detect apparently unused dependencies
- differentiate confidence levels instead of hard yes/no judgments

## Dependency Slimming Advisor

- suggest candidates for removal or scope reduction
- highlight oversized or redundant dependency sets

## AWS Bundle Advisor

- special guidance for large AWS SDK dependency footprints
- help identify opportunities to slim AWS-related dependency bundles

## Suppression System

- local suppression rules for acknowledged findings
- persistent storage for suppressions

## SBOM Import / Export

- export scan data as SBOM-related outputs
- import external SBOM data where practical

## License Analysis

- surface license information for dependencies
- support dependency review and compliance awareness

## Duplicate Class Detection

- detect same class appearing in multiple bundled dependencies
- help diagnose classpath collision risk

## Fat JAR Inspector

- richer inspection of bundled dependencies in application archives
- better nested-library UX

## Policy Engine

- local rules such as severity thresholds, banned licenses, or banned dependencies
- scan pass/fail style policy reporting

## Scan Comparison

- compare two persisted scans
- highlight dependency additions/removals, version changes, and vulnerability deltas

## UI Upgrades

- scan history page
- comparison views
- richer results navigation
- settings surfaces
- dependency tree and drill-down views
