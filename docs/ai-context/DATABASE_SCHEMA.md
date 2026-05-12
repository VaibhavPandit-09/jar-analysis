# Database Schema

## Current v1 Persistence State

As of v1, JARScan does not persist completed scan history in a database.

Current limitation:

- scan jobs and results are stored only in memory for the lifetime of the running container

What does persist today:

- Maven cache in `/root/.m2`
- Dependency-Check cache in `/app/data/dependency-check`

## Planned v2 SQLite Database

Planned database file:

- `/app/data/jarscan.db`

SQLite is planned because it is a good fit for a local-first Dockerized developer tool and avoids adding an external database dependency.

## Planned `scans` Table

Suggested direction:

- `id`
- `created_at`
- `completed_at`
- `status`
- `input_type`
- `input_name`
- `total_artifacts`
- `total_dependencies`
- `vulnerable_dependencies`
- `total_vulnerabilities`
- `critical_count`
- `high_count`
- `medium_count`
- `low_count`
- `info_count`
- `unknown_count`
- `highest_cvss`
- `required_java_version`
- `warning_count`
- `error_count`
- `result_json`

Purpose:

- relational columns support list views, filtering, sorting, comparison, and summary rendering
- `result_json` preserves full detail without requiring many secondary tables up front

## Planned `app_settings` Table

Suggested direction:

- `key`
- `value`
- `updated_at`

Likely settings:

- NVD API key
- default Maven scope
- UI/scan defaults as needed

## Planned `suppressions` Table

Suggested direction:

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
- dependency coordinates

## Planned `policies` Table

Suggested direction:

- `id`
- `name`
- `description`
- `enabled`
- `config_json`
- `created_at`
- `updated_at`

`config_json` can support evolving policy rules without frequent schema churn.

## Summary Columns Plus `result_json` Approach

The recommended storage shape is:

- normalized enough for fast history/search/comparison operations
- denormalized enough to keep implementation practical
- full-fidelity result preserved as raw JSON for exact reopening/export

This avoids needing to decompose every nested artifact and finding into many relational tables in the first persistence session.

## Migration Notes

When v2 persistence is added:

- keep current API result shapes stable where possible
- map current `AnalysisResult` output into stored `result_json`
- compute summary columns from the same result object
- avoid breaking existing frontend result rendering logic
- consider a simple version or migration table once schema evolution begins
