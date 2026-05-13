CREATE TABLE IF NOT EXISTS suppressions (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    group_id TEXT,
    artifact_id TEXT,
    version TEXT,
    cve_id TEXT,
    reason TEXT NOT NULL,
    expires_at TEXT,
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_suppressions_type_active ON suppressions(type, active);
CREATE INDEX IF NOT EXISTS idx_suppressions_coordinates ON suppressions(group_id, artifact_id, version);
CREATE INDEX IF NOT EXISTS idx_suppressions_cve ON suppressions(cve_id);
