# Database Schema

## Current Tables

### `scans`

Stores:
- fast relational summary columns
- full `result_json`
- notes and tags
- input metadata
- status and timestamps

Key columns:
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

### `app_settings`

Stores general application metadata.

Current uses include:
- masked NVD key metadata
- vulnerability DB sync timestamps and status

The raw NVD API key itself is not stored in SQLite.

### `suppressions`

Columns:
- `id`
- `type`
- `group_id`
- `artifact_id`
- `version`
- `cve_id`
- `reason`
- `expires_at`
- `active`
- `created_at`
- `updated_at`

### `policies`

Columns:
- `id`
- `name`
- `description`
- `rule_type`
- `severity`
- `enabled`
- `config_json`
- `created_at`
- `updated_at`

## Persistence Notes

- raw completed results remain in `result_json`
- suppressions and policies are stored separately and applied at read time
- Flyway manages schema evolution
- current schema versions include `V1` through `V4`

## Non-SQLite Local State

Still stored outside SQLite:
- Maven cache
- Dependency-Check data cache
- raw NVD API key secret file
