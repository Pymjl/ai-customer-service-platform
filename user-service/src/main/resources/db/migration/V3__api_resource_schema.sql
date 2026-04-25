CREATE TABLE IF NOT EXISTS cs_api_resource (
    id BIGINT PRIMARY KEY,
    resource_code VARCHAR(256) NOT NULL,
    service_name VARCHAR(128) NOT NULL,
    controller_path VARCHAR(512) NOT NULL,
    controller_name VARCHAR(128) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    path VARCHAR(256) NOT NULL,
    method_name VARCHAR(128) NOT NULL,
    description VARCHAR(1024) NOT NULL DEFAULT '',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cs_api_resource_code ON cs_api_resource (resource_code) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_cs_api_resource_service ON cs_api_resource (service_name) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS cs_role_resource (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cs_role_resource ON cs_role_resource (role_id, resource_id) WHERE deleted = FALSE;
