ALTER TABLE kb_contribution_application ALTER COLUMN source_document_id DROP NOT NULL;
ALTER TABLE kb_contribution_application ADD COLUMN IF NOT EXISTS target_version_no INTEGER;

CREATE TABLE IF NOT EXISTS kb_snapshot_document (
    id BIGINT PRIMARY KEY,
    snapshot_id VARCHAR(64) NOT NULL,
    source_kb_id VARCHAR(64) NOT NULL,
    source_version INTEGER NOT NULL,
    source_document_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    object_path VARCHAR(512) NOT NULL,
    category_id VARCHAR(64),
    product_line VARCHAR(64),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL,
    fingerprint VARCHAR(80) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_kb_snapshot_document_snapshot ON kb_snapshot_document (snapshot_id, source_document_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_kb_snapshot_document_source_kb ON kb_snapshot_document (source_kb_id, source_version) WHERE deleted = FALSE;

ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS source_document_id VARCHAR(64);
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS source_snapshot_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_kb_document_source_snapshot ON kb_document (source_snapshot_id, source_document_id) WHERE deleted = FALSE;
