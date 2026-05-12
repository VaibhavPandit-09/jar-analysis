CREATE TABLE IF NOT EXISTS scans (
    id TEXT PRIMARY KEY,
    job_id TEXT NOT NULL UNIQUE,
    input_type TEXT NOT NULL,
    input_name TEXT,
    input_hash TEXT,
    status TEXT NOT NULL,
    started_at TEXT,
    completed_at TEXT,
    duration_ms INTEGER,
    total_artifacts INTEGER NOT NULL DEFAULT 0,
    total_dependencies INTEGER NOT NULL DEFAULT 0,
    total_vulnerabilities INTEGER NOT NULL DEFAULT 0,
    critical_count INTEGER NOT NULL DEFAULT 0,
    high_count INTEGER NOT NULL DEFAULT 0,
    medium_count INTEGER NOT NULL DEFAULT 0,
    low_count INTEGER NOT NULL DEFAULT 0,
    info_count INTEGER NOT NULL DEFAULT 0,
    unknown_count INTEGER NOT NULL DEFAULT 0,
    highest_cvss REAL,
    required_java_version TEXT,
    created_app_version TEXT,
    notes TEXT,
    tags TEXT NOT NULL DEFAULT '[]',
    result_json TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_scans_created_at ON scans(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_scans_status ON scans(status);
CREATE INDEX IF NOT EXISTS idx_scans_input_type ON scans(input_type);
CREATE INDEX IF NOT EXISTS idx_scans_completed_at ON scans(completed_at DESC);
