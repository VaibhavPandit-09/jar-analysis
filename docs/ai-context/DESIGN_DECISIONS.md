# Design Decisions

## Why Java 25

JARScan targets Java 25 because it is intended to be a long-lived developer tool and should stay on a current LTS platform baseline. Using Java 25 keeps runtime/tooling modern while still allowing analysis of older artifact bytecode.

## Why Docker-First

The project is designed around `docker compose up -d` so users do not need to install the exact Java, Maven, or Dependency-Check toolchain on their host machine. Docker-first execution also reduces environment drift between sessions.

## Why Maven Is Bundled In The Container

Uploaded `pom.xml` analysis depends on real Maven resolution. Bundling Maven in the container ensures:

- the resolver is always available
- the host machine does not need Maven installed
- transitive dependency behavior is consistent across runs

## Why Maven CLI Is Used Instead Of Manually Resolving Dependencies

Maven CLI already handles:

- transitive resolution
- scope semantics
- repository settings
- conflict mediation
- dependency tree generation

Re-implementing resolution manually would add complexity and edge-case risk for little product value.

## Why Dependency-Check Is Used Locally

OWASP Dependency-Check provides a local-first vulnerability scanning path with no mandatory paid service and no mandatory external API key. That aligns with JARScan’s local tool positioning.

## Why SQLite Is Planned For Local Persistence In v2

SQLite fits the product shape well:

- zero external database service
- local-file persistence
- easy Docker volume persistence
- strong fit for scan history, settings, suppressions, and policies

## Why NVD API Key Is Optional

JARScan should remain usable without any external credential. An NVD API key should improve refresh performance and reliability, but it should not become a hard requirement for running the tool.

## Why The NVD API Key Is Stored In A Local Restricted File

For this local-first personal tool, the simplest safe approach is to store the raw NVD API key in a restricted local file under the persisted data directory rather than returning it through APIs or embedding it in exported reports. SQLite still stores related metadata and sync state, but the raw secret stays outside ordinary result and settings payloads.

## Why The UI Only Shows A Masked Key

After a key is saved, the frontend should only receive a masked suffix such as `****abcd`. This reduces accidental exposure in screenshots, logs, and browser devtools while still confirming which local key is active.

## Why Scan Results Should Be Stored As Metadata Plus Full JSON

Summary columns enable:

- fast listing
- filtering
- comparison
- dashboard-style history views

Full JSON storage preserves:

- complete report fidelity
- future re-rendering without recomputation
- export reuse
- schema flexibility as the analysis model evolves

## Why Unused Dependency Detection Should Use Confidence Levels

Unused dependency analysis is inherently probabilistic because Java applications may load dependencies through:

- reflection
- frameworks
- annotation processing side effects
- configuration-driven wiring
- runtime plugin loading

Confidence levels communicate useful guidance without overstating certainty.

## Why Maven dependency:analyze Should Not Be Treated As Absolute Truth

Maven `dependency:analyze` is useful evidence, but it does not fully understand every runtime activation pattern. JARScan combines it with bytecode references, resource/config hints, ServiceLoader/Spring metadata, packaging evidence, and runtime heuristics before suggesting removal.

## Why Slimming Advice Must Stay Review-Oriented

Dependency slimming can break runtime behavior in subtle ways, especially with reflection-heavy frameworks, starters, logging bridges, JDBC drivers, and plugin systems. JARScan should generate explanations, confidence, and copyable snippets, but it should not auto-edit uploaded POM files or promise that removal is safe without testing.

## Why AWS Bundle Advice Is Specialized But Still Best-Effort

Broad AWS SDK bundles can hide a lot of removable weight, so a focused advisor is worthwhile. At the same time, AWS usage can also be configuration-driven or spread across multiple services, so JARScan should infer likely service modules from bytecode and metadata but still require manual review and testing before narrowing a bundle.

## Why This Is Not Source-Code Vulnerability Scanning

JARScan’s scope is dependency intelligence. Expanding into general code-security analysis would change the product into something fundamentally different, increase complexity dramatically, and create false expectations around coverage and correctness.

## Why Project Files May Be Inspected Only For Dependency Analysis

Project files, source files, resources, and compiled classes may still be useful evidence for dependency-focused workflows such as:

- verifying whether a dependency appears referenced
- understanding packaging behavior
- spotting bundled or duplicate classes
- gathering license/SBOM metadata

That inspection is allowed only when it supports dependency analysis, not general application vulnerability scanning.

## Why SSE Is Used For Progress

Scans can take time, especially when Maven resolution or Dependency-Check is involved. SSE is a simple fit for:

- one-way progress updates
- live log streaming
- lower overhead than a heavier real-time channel for this use case

## Why Local-First Matters

Local-first execution supports:

- privacy
- reproducibility
- offline or semi-offline workflows
- use on private artifacts
- lower operational cost

It also keeps JARScan aligned with its intended role as a personal developer tool rather than a hosted scanning platform.
