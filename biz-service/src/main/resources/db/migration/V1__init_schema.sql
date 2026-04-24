CREATE TABLE IF NOT EXISTS cs_session (
    id BIGINT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) DEFAULT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ DEFAULT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cs_session_session_id ON cs_session (session_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_cs_session_user_tenant ON cs_session (user_id, tenant_id) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS cs_message (
    id BIGINT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    user_msg TEXT NOT NULL,
    ai_reply TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ DEFAULT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cs_message_message_id ON cs_message (message_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_cs_message_trace_id ON cs_message (trace_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_cs_message_session ON cs_message (session_id, id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_cs_message_user_tenant ON cs_message (user_id, tenant_id) WHERE deleted = FALSE;
