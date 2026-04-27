-- ai-engine pgvector schema 初始化脚本（IDEA / DataGrip PG Console 可直接执行）
--
-- 说明：
-- 1. 请先连接到 aicsp_engine 数据库，再执行本脚本。
-- 2. 需要 PostgreSQL 已安装 pgvector 扩展。
-- 3. 当前 embedding 模型为 qwen3-embedding:8b，已通过 scripts/check_embedding_dimension.py
--    调用本地 Ollama OpenAI 兼容接口实测向量维度为 4096。
-- 4. 如果本地 Ollama 模型实际返回维度不同，请同步修改：
--    - .env 中 AICSP_EMBEDDING_DIMENSIONS
--    - 本脚本 engine_embedding.embedding vector(4096)

CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS engine_service AUTHORIZATION engine_service;

CREATE TABLE IF NOT EXISTS engine_service.engine_chunk (
    id              BIGSERIAL PRIMARY KEY,
    chunk_id        VARCHAR(96) NOT NULL,
    parent_chunk_id VARCHAR(96),
    document_id     VARCHAR(64) NOT NULL,
    kb_id           VARCHAR(64) NOT NULL,
    kb_version      INTEGER NOT NULL DEFAULT 1,
    kb_type         VARCHAR(32) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    scope           VARCHAR(16) NOT NULL,
    owner_user_id   VARCHAR(64) NULL,
    chunk_type      VARCHAR(32) NOT NULL,
    source_type     VARCHAR(32) NOT NULL,
    case_id         VARCHAR(32),
    case_field      VARCHAR(32),
    section_path    JSONB NOT NULL,
    content         TEXT NOT NULL,
    content_hash    VARCHAR(80) NOT NULL,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    token_count     INTEGER NOT NULL,
    quality_score   NUMERIC(5,4) NOT NULL DEFAULT 1.0,
    status          VARCHAR(32) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_engine_chunk_owner_scope CHECK (
        (scope = 'PUBLIC' AND owner_user_id IS NULL)
        OR (scope = 'PERSONAL' AND owner_user_id IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS engine_service.engine_embedding (
    id          BIGSERIAL PRIMARY KEY,
    chunk_id    VARCHAR(96) NOT NULL,
    model       VARCHAR(128) NOT NULL,
    dimensions  INTEGER NOT NULL,
    embedding   vector(4096) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS engine_service.engine_ingestion_task (
    id            BIGSERIAL PRIMARY KEY,
    task_id       VARCHAR(96) NOT NULL,
    document_id   VARCHAR(64) NOT NULL,
    operation     VARCHAR(32) NOT NULL,
    status        VARCHAR(32) NOT NULL,
    trace_id      VARCHAR(128),
    message       TEXT,
    metrics       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_engine_ingestion_task_id UNIQUE (task_id)
);

CREATE TABLE IF NOT EXISTS engine_service.engine_retrieval_log (
    id                     BIGSERIAL PRIMARY KEY,
    trace_id               VARCHAR(128),
    tenant_id              VARCHAR(64) NOT NULL,
    user_id                VARCHAR(64),
    query_hash             VARCHAR(80) NOT NULL,
    knowledge_selection    JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters                JSONB NOT NULL DEFAULT '{}'::jsonb,
    hit_chunk_ids          JSONB NOT NULL DEFAULT '[]'::jsonb,
    hit_parent_ids         JSONB NOT NULL DEFAULT '[]'::jsonb,
    case_ids               JSONB NOT NULL DEFAULT '[]'::jsonb,
    scores                 JSONB NOT NULL DEFAULT '[]'::jsonb,
    skipped                BOOLEAN NOT NULL DEFAULT FALSE,
    reason                 VARCHAR(128),
    duration_ms            INTEGER NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE engine_service.engine_chunk ADD COLUMN IF NOT EXISTS kb_id VARCHAR(64) NOT NULL DEFAULT 'kb_default';
ALTER TABLE engine_service.engine_chunk ADD COLUMN IF NOT EXISTS kb_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE engine_service.engine_chunk ADD COLUMN IF NOT EXISTS kb_type VARCHAR(32) NOT NULL DEFAULT 'GENERIC_PUBLIC';

CREATE UNIQUE INDEX IF NOT EXISTS uk_engine_chunk_id
    ON engine_service.engine_chunk (chunk_id)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_engine_chunk_doc
    ON engine_service.engine_chunk (document_id)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_engine_chunk_kb
    ON engine_service.engine_chunk (kb_id, kb_version)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_engine_chunk_case
    ON engine_service.engine_chunk (case_id)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_engine_chunk_acl
    ON engine_service.engine_chunk (tenant_id, scope, owner_user_id, status, enabled)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_engine_chunk_public_acl
    ON engine_service.engine_chunk (tenant_id, status, enabled)
    WHERE deleted = FALSE AND scope = 'PUBLIC' AND owner_user_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_engine_embedding_chunk_model
    ON engine_service.engine_embedding (chunk_id, model)
    WHERE deleted = FALSE;

-- 注意：qwen3-embedding:8b 实测为 4096 维，当前 pgvector 的 HNSW 索引不支持
-- 超过 2000 维的 vector 列，因此这里不创建向量 ANN 索引。
-- 第一阶段使用精确向量相似度排序，并依赖 chunk ACL / 文档 / 工单等普通索引先缩小候选集。
-- 如果后续更换为 <= 2000 维 embedding 模型，可再创建：
-- CREATE INDEX IF NOT EXISTS idx_engine_embedding_vector_cosine
--     ON engine_service.engine_embedding
--     USING hnsw (embedding vector_cosine_ops)
--     WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_engine_ingestion_task_doc
    ON engine_service.engine_ingestion_task (document_id, status);

CREATE INDEX IF NOT EXISTS idx_engine_retrieval_log_trace
    ON engine_service.engine_retrieval_log (trace_id);

CREATE INDEX IF NOT EXISTS idx_engine_retrieval_log_tenant_created
    ON engine_service.engine_retrieval_log (tenant_id, created_at DESC);

GRANT USAGE ON SCHEMA engine_service TO engine_service;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA engine_service TO engine_service;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA engine_service TO engine_service;

ALTER DEFAULT PRIVILEGES IN SCHEMA engine_service
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO engine_service;

ALTER DEFAULT PRIVILEGES IN SCHEMA engine_service
    GRANT USAGE, SELECT ON SEQUENCES TO engine_service;
