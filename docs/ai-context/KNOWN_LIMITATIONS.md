# Known Limitations

## Dependency-Check DB Freshness

Dependency-Check results depend on the freshness and health of the local vulnerability database. Cold starts, stale local data, or failed updates can affect finding completeness or timeliness.

## NVD API Key Performance Differences

JARScan should remain usable without an NVD API key, but users should expect slower or more rate-limited update behavior compared with a configured key.

## Private Maven Repository Authentication

Uploaded `pom.xml` resolution depends on the Maven CLI environment inside the container. Private repository credentials, mirrors, or custom settings are not automatically available unless explicitly provided.

## Unused Dependency Confidence Caveats

Future unused dependency analysis should never be treated as perfectly certain. Java applications often use dependencies indirectly through frameworks, generated code, reflection, plugin loading, or runtime configuration.

## Reflection / Configuration / Runtime Loading Caveats

Even when project files or bytecode are inspected, usage evidence may miss dependencies loaded through:

- reflection
- service loaders
- XML/YAML/properties configuration
- container-managed injection
- runtime classpath conventions

## Source Files Are Not Scanned For Application Vulnerabilities

JARScan does not provide general source-code vulnerability scanning. It must not be described or implemented as a scanner for SQL injection, XSS, insecure crypto usage, business-logic flaws, or similar application-security concerns.

## Standalone JARs Without POMs Have Limited Graph Reconstruction

If an uploaded archive lacks embedded Maven metadata or a related POM context, JARScan can still inspect packaged contents but may not be able to reconstruct a full dependency graph accurately.

## Project ZIPs Without Compiled Classes Are Less Reliable For Usage Analysis

Future project ZIP analysis may still be helpful, but if compiled classes are absent then bytecode-backed usage evidence becomes weaker and confidence should be reduced.

## Suggested Exclusions Require Testing

Future outputs such as unused dependency suggestions, slimming advice, or AWS bundle advice should be treated as recommendations that require validation in the user’s build and runtime environment before removal.
