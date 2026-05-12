# Database Schema

## Current Persistence State

As of Session 2, JARScan persists completed scan history in SQLite.

Current database location rules:

- `JARSCAN_DB_PATH` if explicitly provided
- otherwise `${JARSCAN_DATA_DIR}/jarscan.db`
- otherwise `/app/data/jarscan.db`

For local development and tests, the backend may override this to a workspace-local path such as `./data/jarscan.db` or `backend/target/test-data/jarscan.db`.

What still persists outside SQLite:

- Maven cache in `/root/.m2`
- Dependency-Check cache in `/app/data/dependency-check`

What is still in-memory:

- active job registry
- active SSE emitter state and replay buffers

## Current `scans` Table

The current Flyway migration creates a single `scans` table intended to support fast history listing plus exact result reopening.

Columns:

- `id`
- `job_id`
- `input_type`
- `input_name`
- `input_hash`
- `status`
- `started_at`
- `completed_at`
- `duration_ms`
- `total_artifacts`
- `total_dependencies`
- `total_vulnerabilities`
- `critical_count`
- `high_count`
- `medium_count`
- `low_count`
- `info_count`
- `unknown_count`
- `highest_cvss`
- `required_java_version`
- `created_app_version`
- `notes`
- `tags`
- `result_json`
- `created_at`
- `updated_at`

Important constraints and indexes:

- `job_id` is unique
- index on `created_at`
- index on `status`
- index on `input_type`
- index on `completed_at`

## Summary Columns Plus `result_json` Approach

JARScan intentionally stores both:

- relational summary columns for fast listing, filtering, sorting, and future comparison
- full `result_json` for exact result reopening, export reuse, and schema-flexible evolution

This keeps the first persistence layer maintainable without immediately normalizing every nested artifact, dependency, or vulnerability into separate tables.

## Planned `app_settings` Table

Likely direction for Session 4 and later:

- `key`
- `value`
- `updated_at`

Likely settings:

- NVD API key
- default Maven dependency scope
- UI and scan defaults as needed

## Planned `suppressions` Table

Likely direction for Session 10:

- `id`
- `created_at`
- `updated_at`
- `match_type`
- `match_value`
- `reason`
- `expires_at`
- `active`

Possible targets:

- vulnerability identifiers
- package identifiers
- Maven coordinates
- dependency paths

## Planned `policies` Table

Likely direction for Session 10:

- `id`
- `name`
- `description`
- `enabled`
- `config_json`
- `created_at`
- `updated_at`

`config_json` is the expected escape hatch for policy evolution without frequent schema churn.

## Migration Notes

Current schema management:

- Flyway is enabled in the backend
- the initial migration creates the `scans` table

Guidance for future sessions:

- keep current `AnalysisResult` JSON shape stable where practical
- prefer additive migrations over destructive schema changes
- preserve backward compatibility for stored `result_json`
- treat summary columns as denormalized indexes over the full stored result
