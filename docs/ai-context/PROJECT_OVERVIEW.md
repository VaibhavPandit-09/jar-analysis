# Project Overview

## What JARScan Is

JARScan is a local-first Java dependency analysis tool for developers. Its job is to help inspect uploaded Java artifacts and Maven project descriptors so users can understand:

- what dependencies are present
- what dependency versions are packaged or resolved
- what vulnerabilities are associated with those dependencies
- what nested or bundled dependencies exist inside fat JARs, WARs, or EARs
- what Java runtime level the analyzed artifacts require
- what Maven metadata, dependency-tree context, and exportable reports can be produced

JARScan is intentionally Docker-first and personal-tool oriented. Simplicity, repeatability, local execution, and useful output matter more than enterprise workflow complexity.

## What JARScan Is Not

JARScan is not a Veracode-style source-code vulnerability scanner.

It should not be extended into a general application security analyzer that inspects source code for SQL injection, XSS, auth flaws, insecure deserialization, business logic issues, or similar code-level vulnerabilities.

JARScan may inspect:

- project files
- source files
- compiled classes
- WAR/EAR/JAR contents
- Maven metadata
- bytecode references
- resources
- configuration files

but only to support dependency analysis use cases such as:

- dependency presence and usage evidence
- dependency conflicts and convergence issues
- packaged dependency inspection
- vulnerable dependency discovery
- apparently unused dependency detection
- dependency paths and graph explanations
- license attribution
- SBOM import/export
- scan history and comparison

## Supported Inputs

As of v1, the application supports:

- `.jar`
- `.war`
- `.ear`
- `pom.xml`

Current UX supports:

- drag-and-drop upload
- file-picker upload
- multiple archive uploads in one job
- a single `pom.xml` upload for Maven-based resolution

## High-Level v1 Features

Current v1 capabilities include:

- Java 25 Spring Boot backend
- React + Vite + TypeScript frontend
- Tailwind CSS with Radix/shadcn-style UI primitives
- Docker Compose deployment
- bundled Maven CLI inside the runtime container
- bundled OWASP Dependency-Check CLI inside the runtime container
- Maven transitive dependency resolution for uploaded `pom.xml`
- bytecode class-version inspection
- manifest parsing
- Maven coordinate extraction from archive metadata
- fat JAR / nested library detection
- SSE progress updates
- Maven log streaming in the UI
- local vulnerability scanning
- JSON, Markdown, and HTML report export
- light, dark, and system theme support

## v2 Roadmap Summary

Planned v2 work is intentionally split across 10 sessions:

1. AI context docs and roadmap foundation
2. SQLite persistent scan history backend
3. Scan history UI and reopening prior scan results
4. NVD API key settings, Dependency-Check DB status, and manual sync UI
5. Scan comparison
6. Project ZIP upload, WAR/EAR deep inspection, and fat JAR inspector
7. Maven dependency tree parser, dependency tree visualization, and path-to-dependency view
8. Version conflict analysis, convergence analysis, duplicate class detection, and license analysis
9. Dependency usage analysis, unused dependency confidence scoring, dependency slimming advice, and AWS bundle advice
10. Suppressions, SBOM support, policy engine, final UI polish, final docs, and tests

## Core Design Principles

- Local-first: scans should run on the user's machine or local container without mandatory SaaS dependencies.
- Dependency-focused: all analysis should stay centered on dependency intelligence, not generic source-code vulnerability scanning.
- Docker-first: `docker compose up -d` should remain the primary run path.
- Maven-native resolution: Maven should stay the authoritative resolver for uploaded `pom.xml` files.
- Honest confidence levels: advanced analysis such as unused dependency detection should communicate uncertainty clearly.
- Useful persistence: scan history should preserve both structured summary metadata and full raw result JSON.
- Maintainable architecture: backend and frontend should stay readable enough that future AI sessions can work incrementally.
