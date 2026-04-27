CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id BIGINT PRIMARY KEY,
    kb_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    owner_user_id VARCHAR(64),
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    kb_type VARCHAR(32) NOT NULL,
    source_kb_id VARCHAR(64),
    source_version INTEGER,
    current_version INTEGER NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_kb_base_owner_scope CHECK (
        (scope = 'PUBLIC' AND owner_user_id IS NULL)
        OR (scope = 'PERSONAL' AND owner_user_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_base_kb_id ON kb_knowledge_base (kb_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_kb_base_scope_owner ON kb_knowledge_base (tenant_id, scope, owner_user_id) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_case_library ON kb_knowledge_base (tenant_id, kb_type) WHERE deleted = FALSE AND kb_type = 'CASE_LIBRARY';

CREATE TABLE IF NOT EXISTS kb_version (
    id BIGINT PRIMARY KEY,
    version_id VARCHAR(64) NOT NULL,
    kb_id VARCHAR(64) NOT NULL,
    version_no INTEGER NOT NULL,
    version_type VARCHAR(32) NOT NULL,
    source_kb_id VARCHAR(64),
    source_version_no INTEGER,
    note VARCHAR(512),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_version_id ON kb_version (version_id) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_version_no ON kb_version (kb_id, version_no) WHERE deleted = FALSE;

ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS kb_id VARCHAR(64);
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS kb_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE kb_ingestion_task ADD COLUMN IF NOT EXISTS kb_id VARCHAR(64);
ALTER TABLE kb_ingestion_task ADD COLUMN IF NOT EXISTS kb_version INTEGER NOT NULL DEFAULT 1;

INSERT INTO kb_knowledge_base (
    id, kb_id, tenant_id, scope, owner_user_id, name, description, kb_type,
    current_version, enabled, status, locked, created_by, updated_by, deleted
)
SELECT
    ABS(('x' || SUBSTR(MD5('kb_case_default_' || tenant_id), 1, 15))::bit(60)::bigint),
    'kb_case_default_' || tenant_id,
    tenant_id,
    'PUBLIC',
    NULL,
    '客服案例库',
    '系统默认公共客服案例库',
    'CASE_LIBRARY',
    1,
    TRUE,
    'ACTIVE',
    TRUE,
    0,
    0,
    FALSE
FROM (SELECT DISTINCT tenant_id FROM kb_document WHERE deleted = FALSE UNION SELECT 'default') tenants
ON CONFLICT DO NOTHING;

INSERT INTO kb_version (
    id, version_id, kb_id, version_no, version_type, note, created_by, deleted
)
SELECT
    ABS(('x' || SUBSTR(MD5('ver_' || kb_id || '_1'), 1, 15))::bit(60)::bigint),
    'ver_' || kb_id || '_1',
    kb_id,
    1,
    'INITIAL',
    '初始化版本',
    0,
    FALSE
FROM kb_knowledge_base
WHERE deleted = FALSE
ON CONFLICT DO NOTHING;

UPDATE kb_document d
SET kb_id = 'kb_case_default_' || d.tenant_id
WHERE d.kb_id IS NULL
  AND d.scope = 'PUBLIC';

INSERT INTO kb_knowledge_base (
    id, kb_id, tenant_id, scope, owner_user_id, name, description, kb_type,
    current_version, enabled, status, locked, created_by, updated_by, deleted
)
SELECT
    ABS(('x' || SUBSTR(MD5('kb_personal_default_' || tenant_id || '_' || owner_user_id), 1, 15))::bit(60)::bigint),
    'kb_personal_default_' || SUBSTR(MD5(tenant_id || '_' || owner_user_id), 1, 32),
    tenant_id,
    'PERSONAL',
    owner_user_id,
    '我的默认知识库',
    '兼容旧文档自动生成的个人知识库',
    'PERSONAL',
    1,
    TRUE,
    'ACTIVE',
    FALSE,
    0,
    0,
    FALSE
FROM (
    SELECT DISTINCT tenant_id, owner_user_id
    FROM kb_document
    WHERE deleted = FALSE AND scope = 'PERSONAL' AND owner_user_id IS NOT NULL
) personal
ON CONFLICT DO NOTHING;

UPDATE kb_document d
SET kb_id = 'kb_personal_default_' || SUBSTR(MD5(d.tenant_id || '_' || d.owner_user_id), 1, 32)
WHERE d.kb_id IS NULL
  AND d.scope = 'PERSONAL'
  AND d.owner_user_id IS NOT NULL;

ALTER TABLE kb_document ALTER COLUMN kb_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_kb_document_kb ON kb_document (tenant_id, kb_id, status, enabled) WHERE deleted = FALSE;

ALTER TABLE kb_operation_log ADD COLUMN IF NOT EXISTS kb_id VARCHAR(64);
ALTER TABLE kb_contribution_application ADD COLUMN IF NOT EXISTS application_type VARCHAR(32) NOT NULL DEFAULT 'CONTRIBUTE';
ALTER TABLE kb_contribution_application ADD COLUMN IF NOT EXISTS source_kb_id VARCHAR(64);
ALTER TABLE kb_contribution_application ADD COLUMN IF NOT EXISTS source_snapshot_id VARCHAR(64);
ALTER TABLE kb_contribution_application ADD COLUMN IF NOT EXISTS target_kb_id VARCHAR(64);
ALTER TABLE kb_contribution_application ADD COLUMN IF NOT EXISTS review_comment TEXT;
ALTER TABLE kb_contribution_application ADD COLUMN IF NOT EXISTS reviewer_user_id VARCHAR(64);
ALTER TABLE kb_contribution_application ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;
