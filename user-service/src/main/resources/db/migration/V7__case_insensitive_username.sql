CREATE UNIQUE INDEX IF NOT EXISTS uk_cs_user_tenant_username_lower
    ON cs_user (tenant_id, lower(username))
    WHERE deleted = FALSE;
