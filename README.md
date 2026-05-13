# JARScan v2

JARScan is a local-first Java dependency intelligence tool for JAR, WAR, EAR, Maven POM, project ZIP, and CycloneDX JSON SBOM inputs.

It is designed to answer dependency-focused questions such as:
- What libraries are inside this archive or build?
- Which Java version does it require?
- Which dependencies are vulnerable?
- Why is a transitive dependency present?
- Where do version conflicts, duplicate classes, or license risks exist?
- Which dependencies look removable based on evidence?
- Which findings should be suppressed or enforced by policy?

JARScan is not a Veracode-style source-code vulnerability scanner. It may inspect source files, resources, bytecode, and metadata only to understand dependency usage and packaging behavior.

## What Is New In v2

v2 adds:
- persistent scan history in SQLite
- reopenable scan results and scan comparison
- NVD API key management in the UI
- Dependency-Check DB status and manual sync
- project ZIP upload with safe extraction and structure detection
- Maven dependency tree parsing and visualization
- path-to-dependency explanations
- version conflict and dependency convergence analysis
- duplicate class and split-package detection
- license analysis and summary categories
- evidence-based dependency usage analysis with confidence levels
- dependency slimming advisor
- AWS bundle advisor
- suppressions and suppression management
- policy engine and policy results
- CycloneDX JSON SBOM import and export

## Supported Inputs

- one or more `.jar`, `.war`, `.ear` files
- one `pom.xml`
- one project `.zip`
- one CycloneDX `.json`, `.cdx.json`, or `.bom.json`

## Persistent Scan History

Completed scans are persisted in SQLite and exposed in the Scan History page.

You can:
- reopen old results
- compare two stored scans
- export prior scans
- add notes and tags
- preserve results across container restarts

## NVD API Key Setup

An NVD API key is optional.

If configured:
- it is stored locally on disk
- the UI shows only a masked suffix
- the raw key is not returned by the API
- Dependency-Check can update faster on cold caches

The Settings page supports:
- save key
- test key
- delete key
- inspect current masked status

## Dependency-Check DB Sync

The Settings page and top navigation expose:
- current Dependency-Check DB status
- last sync timestamps and duration
- whether an NVD API key is configured
- a manual sync action
- a sync event log

## Project ZIP Upload

Project ZIP mode safely extracts archives with:
- zip-slip protection
- extracted-size limits
- file-count limits
- best-effort root `pom.xml` detection
- compiled class and dependency-library directory detection

## Dependency Tree Visualization

For POM and Maven-backed project ZIP scans, JARScan captures Maven `dependency:tree` output, prefers JSON when available, and falls back to text parsing when JSON is unavailable.

The Dependency Tree tab supports:
- expand and collapse
- expand all and collapse all
- search
- scope filtering
- direct/transitive filtering
- vulnerable node highlighting
- conflict and omitted node highlighting
- side-panel details

## Path To Dependency

JARScan can explain why a dependency is present by showing one or more paths from the root project to the selected dependency.

This is available from:
- the Dependency Tree tab
- vulnerability rows with `Show path` when a dependency-tree match exists

## Usage Analysis And Confidence Levels

Dependency usage analysis is evidence-based, not absolute.

Statuses include:
- `USED`
- `APPARENTLY_UNUSED`
- `POSSIBLY_RUNTIME_USED`
- `UNKNOWN`
- `USED_UNDECLARED`
- `DECLARED_BUT_UNUSED`
- `PACKAGED_BUT_APPARENTLY_UNUSED`
- `RESOLVED_BUT_NOT_PACKAGED`
- `PACKAGED_BUT_NOT_RESOLVED`

Each finding includes:
- confidence: `HIGH`, `MEDIUM`, or `LOW`
- evidence
- warnings
- suggested action

Evidence sources include:
- Maven `dependency:analyze`
- bytecode constant-pool references
- packaged archive contents
- ServiceLoader metadata
- Spring metadata
- resource and config hints
- runtime/framework heuristics
- source-import fallback when compiled classes are absent

## Dependency Slimming Advisor

The Slimming Advisor combines:
- usage evidence
- size impact
- vulnerability contribution
- transitive fan-out
- version conflicts
- duplicate/provider overlap
- AWS bundle narrowing guidance

It generates copyable snippets where possible:
- Maven dependency snippets
- exclusion snippets

## AWS Bundle Advisor

JARScan special-cases broad AWS SDK bundle usage.

If a broad AWS bundle is detected, the advisor highlights:
- used AWS service modules inferred from bytecode
- apparently unused service modules
- narrower replacement suggestions such as `software.amazon.awssdk:s3`
- copyable Maven snippets

## Version Conflicts

JARScan detects:
- multiple requested versions of the same `groupId:artifactId`
- resolved version
- introducing paths
- omitted/conflict signals from Maven tree output
- dependencyManagement snippet suggestions

## Dependency Convergence

JARScan reports convergence findings from the parsed Maven tree even when Maven already selected a single winning version.

## Duplicate Classes

JARScan can report:
- exact duplicate classes across JARs
- split packages
- a few high-signal runtime collision patterns such as multiple SLF4J bindings and version skew in common families

## License Analysis

License extraction is best-effort and uses:
- embedded Maven `pom.xml`
- Maven metadata already captured by the scan
- `LICENSE` and `NOTICE` files
- manifest evidence
- imported SBOM license declarations

Categories include:
- permissive
- weak copyleft
- strong copyleft
- commercial
- unknown
- multiple

## Suppressions

Suppressions are persisted in SQLite and can be created from the results UI or managed on the Suppressions page.

Supported suppression types:
- vulnerability
- license
- policy
- dependency
- version conflict
- duplicate class
- usage

Suppressed findings are not deleted. They are returned with suppression metadata and can be shown or hidden in the UI.

## Policies

Policies are persisted in SQLite and evaluated against reopened scans.

Built-in policies include:
- fail if critical vulnerabilities exist
- warn if high vulnerabilities exist
- warn if unknown licenses exist
- fail if strong copyleft licenses exist
- warn if dependencies are apparently unused
- warn if duplicate classes exist
- warn if multiple versions of the same artifact exist
- warn if required Java exceeds a configured limit
- warn if SNAPSHOT dependencies exist
- warn if broad bundle dependencies exist

## SBOM Import And Export

Export:
- JSON
- Markdown
- HTML
- CycloneDX JSON

Import:
- CycloneDX JSON SBOMs through the Upload page or `POST /api/sbom/import`

Imported SBOMs are persisted as stored scans so they can be:
- reopened
- compared
- exported again
- evaluated by policies

## Scan Comparison

The Compare page supports two stored scans and highlights:
- dependency additions, removals, and updates
- vulnerability additions, fixes, and changes
- high-level summary deltas

## Data Storage

Primary persistent state:
- SQLite database at `${JARSCAN_DB_PATH}` or `${JARSCAN_DATA_DIR}/jarscan.db`

Other local state:
- Dependency-Check data directory
- Maven cache
- local NVD API key secret file

## Security Model

JARScan is local-first and does not execute uploaded code.

It analyzes:
- archive contents
- manifests
- metadata
- compiled bytecode bytes
- Maven command output
- resource/config files

It does not perform application source-code vulnerability scanning.

## Running Locally

### Docker Compose

```bash
docker compose up -d --build
```

Frontend and backend are served together at:
- [http://localhost:8080](http://localhost:8080)

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

## Important API Endpoints

- `POST /api/jobs`
- `GET /api/jobs/{jobId}/status`
- `GET /api/jobs/{jobId}/result`
- `GET /api/jobs/{jobId}/export?format=json|markdown|html|cyclonedx-json`
- `GET /api/scans`
- `GET /api/scans/{scanId}`
- `GET /api/scans/{scanId}/export?format=json|markdown|html|cyclonedx-json`
- `GET /api/scans/by-job/{jobId}`
- `GET /api/compare?base={scanId}&target={scanId}`
- `GET /api/settings/nvd`
- `POST /api/settings/nvd`
- `POST /api/settings/nvd/test`
- `DELETE /api/settings/nvd`
- `GET /api/vulnerability-db/status`
- `POST /api/vulnerability-db/sync`
- `GET /api/suppressions`
- `POST /api/suppressions`
- `PATCH /api/suppressions/{id}`
- `DELETE /api/suppressions/{id}`
- `GET /api/policies`
- `POST /api/policies`
- `PATCH /api/policies/{id}`
- `DELETE /api/policies/{id}`
- `POST /api/policies/evaluate/{scanId}`
- `POST /api/sbom/import`

## Known Limitations

1. Dependency-Check DB freshness
   Dependency-Check findings depend on its local database state. Cold-start updates can be slow, especially without an NVD API key.
2. Optional NVD API key
   An NVD API key can improve update speed. If configured, it is stored locally and masked in UI and logs.
3. Private Maven repositories
   Private repositories require mounted `settings.xml` or other Maven credential configuration. JARScan does not automatically authenticate to private repositories.
4. Unused dependency analysis is evidence-based
   Java dependencies may be loaded through reflection, configuration, ServiceLoader, Spring auto-configuration, servlet containers, logging frameworks, JDBC drivers, or runtime plugins.
5. Not source-code vulnerability scanning
   JARScan may inspect source files, resources, and bytecode to understand dependency usage only. It does not perform application vulnerability scanning.
6. Standalone archives have limited graph reconstruction
   Standalone JAR/WAR/EAR uploads without usable POM metadata may not provide enough information to reconstruct the full Maven dependency graph.
7. Source-only project ZIPs are less reliable
   If compiled classes are absent, usage analysis falls back to weaker source/import/resource heuristics.
8. Suggested exclusions require testing
   Generated exclusions and dependencyManagement snippets are recommendations only.
9. SBOM import limitations
   Imported SBOM quality depends on the source SBOM completeness.

## AI Maintainer Context

Maintainer docs live under `docs/ai-context/` and now reflect the final v2 architecture, APIs, persistence model, and v3 ideas.
