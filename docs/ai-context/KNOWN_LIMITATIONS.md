# Known Limitations

## Dependency-Check DB Freshness

Dependency-Check results depend on the freshness and health of the local vulnerability database. Cold starts, stale local data, or failed updates can affect finding completeness or timeliness.

## NVD API Key Performance Differences

JARScan remains usable without an NVD API key, but users should expect slower or more rate-limited database update behavior compared with a configured key.

## Local Secret Storage Depends On Host Security

The NVD API key is masked in the UI and never returned after save, but it is still stored locally on disk for this Docker-first workflow. Anyone with access to the host filesystem or persisted Docker volume may still be able to access the configured secret file.

## Private Maven Repository Authentication

Uploaded `pom.xml` resolution depends on the Maven CLI environment inside the container. Private repository credentials, mirrors, or custom settings are not automatically available unless explicitly provided.

## Running Jobs And Live SSE State Are Still In-Memory

Completed scan history is now persisted in SQLite under `/app/data/jarscan.db`, but active jobs, live progress state, and SSE replay buffers are still runtime-only. A container restart during an in-flight job will still lose that active execution state.

## NVD Key Validation Is Best-Effort

The settings page performs best-effort local validation of the stored NVD API key format. It does not guarantee that the key is currently accepted by NVD or that upstream services are reachable.

## Unused Dependency Confidence Caveats

Future unused dependency analysis should never be treated as perfectly certain. Java applications often use dependencies indirectly through frameworks, generated code, reflection, plugin loading, or runtime configuration.

## Reflection / Configuration / Runtime Loading Caveats

Even when project files or bytecode are inspected, usage evidence may miss dependencies loaded through:

- reflection
- service loaders
- XML, YAML, or properties configuration
- container-managed injection
- runtime classpath conventions

## Source Files Are Not Scanned For Application Vulnerabilities

JARScan does not provide general source-code vulnerability scanning. It must not be described or implemented as a scanner for SQL injection, XSS, insecure crypto usage, business-logic flaws, or similar application-security concerns.

## Standalone JARs Without POMs Have Limited Graph Reconstruction

If an uploaded archive lacks embedded Maven metadata or related POM context, JARScan can still inspect packaged contents but may not be able to reconstruct a full dependency graph accurately.

## Dependency Tree Visualization Depends On Maven Tree Output

The Session 7 dependency tree UI only appears when Maven `dependency:tree` output could be captured and parsed from an uploaded `pom.xml` or a project ZIP with a usable root `pom.xml`.

## Version Conflict And Convergence Findings Depend On Tree Fidelity

Session 8 conflict and convergence analysis are only as precise as the parsed Maven tree. When Maven output includes omitted nodes, managed-version hints, or conflict annotations, JARScan can explain more. When that context is missing, findings remain best-effort.

## Duplicate Class Detection Is Bounded

Duplicate class analysis intentionally stops after configurable archive-count and internal entry-count limits so very large scans do not become excessively expensive. A clean run means "no duplicates detected within the scan budget," not an absolute proof for arbitrarily large inputs.

## Duplicate Class Detection Is Archive-Centric

Duplicate-class and split-package findings are derived from the analyzed dependency archive set. They do not reconstruct the exact runtime classpath order for every application server, launcher, shading strategy, or container environment.

## License Analysis Is Best-Effort

License findings rely on evidence such as embedded Maven `pom.xml`, manifest metadata, and `LICENSE`/`NOTICE` files. Some artifacts provide incomplete, conflicting, or missing license data, so unknown and multiple-license results should be reviewed manually.

## License Categories Are Review Aids, Not Legal Advice

Permissive, weak-copyleft, strong-copyleft, commercial, unknown, and multiple classifications are intended to help triage dependency review. They are not legal advice and do not replace a proper license compliance process.

## Project ZIPs Without Compiled Classes Are Less Reliable For Usage Analysis

Future project ZIP analysis may still be helpful, but if compiled classes are absent then bytecode-backed usage evidence becomes weaker and confidence should be reduced.

## Project ZIPs Without POMs Have Reduced Resolution Fidelity

If a project ZIP does not contain a usable root `pom.xml`, JARScan can still inspect packaged archives and detected library directories, but Maven-backed dependency resolution and dependency-tree output will be unavailable.

## Safe Extraction Limits Can Reject Very Large Project ZIPs

Project ZIP extraction is intentionally bounded by file-count and extracted-size limits. Large monorepos, generated directories, or oversized vendor bundles may be rejected unless limits are raised intentionally.

## WAR/EAR And Fat JAR Inspection Still Has Runtime Blind Spots

Current WAR/EAR/fat JAR inspection now includes duplicate-class analysis against the analyzed archive set, but it still focuses on packaged structure, embedded libraries, metadata, and bytecode versions. It does not fully model runtime container ordering, dynamic class loading, or usage analysis inside those layouts.

## Suggested Exclusions Require Testing

Future outputs such as unused dependency suggestions, slimming advice, or AWS bundle advice should be treated as recommendations that require validation in the user’s build and runtime environment before removal.
