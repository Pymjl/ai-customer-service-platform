INSERT INTO cs_role (id, role_code, role_name, created_by, updated_by)
VALUES (1001, 'ADMIN', '系统管理员', 0, 0)
ON CONFLICT DO NOTHING;

INSERT INTO cs_role (id, role_code, role_name, created_by, updated_by)
VALUES (1002, 'USER', '普通用户', 0, 0)
ON CONFLICT DO NOTHING;

INSERT INTO cs_permission (id, permission_code, permission_name, created_by, updated_by)
VALUES (1001, 'USER_MANAGE', '用户管理', 0, 0),
       (1002, 'ROLE_MANAGE', '角色管理', 0, 0),
       (1003, 'RESOURCE_MANAGE', '资源管理', 0, 0)
ON CONFLICT DO NOTHING;

INSERT INTO cs_user (id, user_id, tenant_id, username, password, status, created_by, updated_by)
VALUES (1001, 'U1001', 'default', 'admin', '{noop}admin123', 1, 0, 0)
ON CONFLICT DO NOTHING;

INSERT INTO cs_user_role (id, user_id, role_id, created_by, updated_by)
VALUES (1001, 'U1001', 1001, 0, 0)
ON CONFLICT DO NOTHING;
