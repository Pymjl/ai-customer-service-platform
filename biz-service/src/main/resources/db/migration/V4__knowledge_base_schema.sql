CREATE TABLE IF NOT EXISTS kb_document (
    id BIGINT PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    owner_user_id VARCHAR(64),
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    object_path VARCHAR(512) NOT NULL,
    category_id VARCHAR(64),
    product_line VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_kb_document_scope_owner CHECK (
        (scope = 'PUBLIC' AND owner_user_id IS NULL)
        OR (scope = 'PERSONAL' AND owner_user_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_document_document_id ON kb_document (document_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_kb_document_scope_owner ON kb_document (tenant_id, scope, owner_user_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_kb_document_selectable ON kb_document (tenant_id, scope, status, enabled) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS kb_category (
    id BIGINT PRIMARY KEY,
    category_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    owner_user_id VARCHAR(64),
    parent_id VARCHAR(64),
    name VARCHAR(128) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_kb_category_scope_owner CHECK (
        (scope = 'PUBLIC' AND owner_user_id IS NULL)
        OR (scope = 'PERSONAL' AND owner_user_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_category_category_id ON kb_category (category_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_kb_category_scope ON kb_category (tenant_id, scope, owner_user_id) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS kb_tag (
    id BIGINT PRIMARY KEY,
    tag_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_tag_tag_id ON kb_tag (tag_id) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_tag_name ON kb_tag (tenant_id, name) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS kb_document_tag (
    document_id VARCHAR(64) NOT NULL,
    tag_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (document_id, tag_id)
);

CREATE TABLE IF NOT EXISTS kb_ingestion_task (
    task_id VARCHAR(64) NOT NULL PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    embedding_model VARCHAR(128),
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_ingestion_document ON kb_ingestion_task (document_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS kb_operation_log (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64),
    operation VARCHAR(64) NOT NULL,
    operator_user_id VARCHAR(64) NOT NULL,
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_contribution_application (
    id BIGINT PRIMARY KEY,
    application_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    source_document_id VARCHAR(64) NOT NULL,
    applicant_user_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    target_category_id VARCHAR(64),
    reason TEXT,
    reject_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_contribution_application_id ON kb_contribution_application (application_id) WHERE deleted = FALSE;
