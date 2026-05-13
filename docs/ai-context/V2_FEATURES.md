# V2 Features

## Completed v2 Scope

v2 now includes:
- persistent scan history in SQLite
- reopenable results
- scan comparison
- NVD API key management
- vulnerability DB status and manual sync
- project ZIP upload and safe extraction
- WAR/EAR deep inspection
- fat JAR inspector
- Maven dependency tree parsing
- dependency tree visualization
- path-to-dependency view
- version conflict analysis
- dependency convergence analysis
- duplicate class detection
- split-package detection
- license analysis
- evidence-based dependency usage analysis
- dependency slimming advisor
- AWS bundle advisor
- suppressions
- policy engine
- CycloneDX JSON SBOM import/export
- final UI polish for key v2 pages

## Feature Notes

### Dependency Tree
- JSON-first tree capture with text fallback
- search/filter/highlight support in the UI

### Usage Analysis
- confidence-based
- combines Maven, bytecode, resources, ServiceLoader, Spring metadata, and heuristics

### Policies
- stored in SQLite
- reevaluated for reopened scans
- support enable/disable and simple config editing

### Suppressions
- stored in SQLite
- do not delete raw findings
- include reason and optional expiry
