# JARScan

JARScan is a personal web-based Java artifact analyzer for `.jar`, `.war`, `.ear`, and `pom.xml` inputs. It resolves Maven dependencies inside the container, inspects archive metadata without executing uploaded code, detects nested libraries, streams progress over Server-Sent Events, and folds local vulnerability findings into a modern React dashboard.

## Features

- Drag-and-drop or browse uploads for one or more archives
- Single `pom.xml` upload with Maven transitive dependency resolution
- Java bytecode version inspection from class headers
- Manifest parsing and Maven coordinate extraction
- Nested/fat JAR detection for common layouts such as `BOOT-INF/lib/`
- Live analysis progress and Maven log streaming
- Local vulnerability scanning through OWASP Dependency-Check CLI
- JSON, Markdown, and HTML report export
- Light, dark, and system theme support

## AI Maintainer Context

Before making broad changes, future AI agents should read:

- `docs/ai-context/PROJECT_OVERVIEW.md`
- `docs/ai-context/ARCHITECTURE.md`
- `docs/ai-context/DESIGN_DECISIONS.md`
- `docs/ai-context/NEXT_STEPS.md`

## Screenshots

- Placeholder: upload screen
- Placeholder: live progress screen
- Placeholder: results dashboard

## Stack

- Backend: Java 25, Spring Boot 3.5, Maven
- Frontend: React, Vite, TypeScript, Tailwind CSS, Radix primitives, shadcn-style UI components
- Vulnerability scanning: OWASP Dependency-Check CLI with persisted local data directory
- Container base image: `maven:3.9.14-eclipse-temurin-25`
- Dependency-Check CLI bundled in the final runtime image: `12.2.1`

## Run With Docker Compose

```bash
docker compose up -d --build
```

Open [http://localhost:8080](http://localhost:8080).

## What Happens On Upload

### Archive upload

1. Files are copied into a job-specific temporary workspace.
2. JARScan inspects archive entries, hashes, manifests, bytecode versions, Maven metadata, and nested libraries.
3. Dependency-Check scans the extracted artifact set if its CLI is available.
4. Results are exposed through the results API and UI, and completed scans are persisted into local SQLite history.

### `pom.xml` upload

1. The uploaded `pom.xml` is stored in a temporary workspace.
2. Maven runs inside the container with `ProcessBuilder`.
3. `dependency:copy-dependencies` resolves and downloads transitive dependencies into a job-local directory.
4. `dependency:tree` output is captured for later display/export.
5. Each resolved artifact is analyzed like a direct archive upload.

## Volumes And Persistence

`docker-compose.yml` mounts:

- `jarscan-data` to `/app/data`
  - Dependency-Check data cache lives under `/app/data/dependency-check`
- `jarscan-m2` to `/root/.m2`
  - Maven dependency cache for faster repeated POM analysis

## Persistent Scan History

Completed scans are stored in a local SQLite database so history survives container restarts and image rebuilds as long as the Docker volume is preserved.

- Default database path: `/app/data/jarscan.db`
- Override with `JARSCAN_DB_PATH`
- The database lives under the `jarscan-data` volume by default
- Deleting Docker volumes with `docker compose down -v` also deletes stored scan history

## Local Development

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

The Vite dev server proxies `/api` requests to `http://localhost:8080`.

## Configuration

Environment variables supported by the backend:

- `JARSCAN_DATA_DIR`
- `JARSCAN_DB_PATH`
- `JARSCAN_DEPENDENCY_CHECK_COMMAND`
- `JARSCAN_MAX_UPLOAD_SIZE`
- `JARSCAN_MAVEN_TIMEOUT_SECONDS`
- `JARSCAN_MAVEN_DEPENDENCY_SCOPE`
- `JARSCAN_MAX_NESTED_JAR_DEPTH`
- `JARSCAN_MAX_EXTRACTED_ARCHIVE_SIZE_BYTES`

## Export Formats

- JSON: `/api/jobs/{jobId}/export?format=json`
- Markdown: `/api/jobs/{jobId}/export?format=markdown`
- HTML: `/api/jobs/{jobId}/export?format=html`

## Resetting Data

To remove persisted Maven and vulnerability caches:

```bash
docker compose down -v
```

## Troubleshooting

### First vulnerability DB sync is slow

Dependency-Check may need to initialize or refresh its local database on first use. The first scan or manual sync can take several minutes depending on network conditions.

### Maven resolution failed

Check the live log panel on the job page. Common causes:

- broken or non-buildable uploaded `pom.xml`
- private repositories requiring credentials
- corporate mirrors not reachable from the container

### POM uses private repositories

JARScan intentionally relies on the Maven CLI in-container. If your POM needs private credentials or custom Maven settings, mount or bake in the required Maven configuration before running scans.

### Large projects take time

POM resolution plus Dependency-Check can be expensive on very large graphs. The Maven cache and Dependency-Check data cache will reduce repeated run times.

### Frontend route refresh returns 404

The backend includes SPA forwarding for `/jobs/:jobId` and `/jobs/:jobId/results`. Rebuild the image if you are running an older container.

### Java 25 image concerns

The project is built and run on Eclipse Temurin Java 25 through the official Maven image `maven:3.9.14-eclipse-temurin-25`.

## Known Limitations

- Dependency-Check findings depend on its local database state and may take time on a cold start.
- Private Maven repositories are not automatically authenticated.
- Running jobs and SSE event streams are still in-memory while the job is active, even though completed scan results now persist in SQLite history.
- Dependency tables on individual artifact cards focus on embedded nested archives rather than reconstructing a full Maven graph per artifact.
