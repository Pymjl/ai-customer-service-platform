INSERT INTO cs_role (id, role_code, role_name, created_by, updated_by)
VALUES (1000, 'SUPER_ADMIN', U&'\8D85\7EA7\7BA1\7406\5458', 0, 0),
       (1001, 'ADMIN', U&'\7BA1\7406\5458', 0, 0),
       (1002, 'USER', U&'\666E\901A\7528\6237', 0, 0)
ON CONFLICT DO NOTHING;

UPDATE cs_role SET role_name = U&'\8D85\7EA7\7BA1\7406\5458', updated_at = CURRENT_TIMESTAMP WHERE role_code = 'SUPER_ADMIN' AND deleted = FALSE;
UPDATE cs_role SET role_name = U&'\7BA1\7406\5458', updated_at = CURRENT_TIMESTAMP WHERE role_code = 'ADMIN' AND deleted = FALSE;
UPDATE cs_role SET role_name = U&'\666E\901A\7528\6237', updated_at = CURRENT_TIMESTAMP WHERE role_code = 'USER' AND deleted = FALSE;

INSERT INTO cs_api_resource (id, resource_code, service_name, controller_path, controller_name, http_method, path, method_name, description, enabled)
VALUES
  (2001, 'user-service:GET:/api/users', 'user-service', 'com/aicsp/user/controller/UserController.java', 'UserController', 'GET', '/api/users', 'listUsers', 'List users', TRUE),
  (2002, 'user-service:POST:/api/users', 'user-service', 'com/aicsp/user/controller/UserController.java', 'UserController', 'POST', '/api/users', 'createUser', 'Create user', TRUE),
  (2003, 'user-service:GET:/api/users/{userId}', 'user-service', 'com/aicsp/user/controller/UserController.java', 'UserController', 'GET', '/api/users/{userId}', 'getUser', 'Get user profile', TRUE),
  (2004, 'user-service:PUT:/api/users/{userId}', 'user-service', 'com/aicsp/user/controller/UserController.java', 'UserController', 'PUT', '/api/users/{userId}', 'updateUser', 'Update user profile', TRUE),
  (2005, 'user-service:PUT:/api/users/{userId}/avatar', 'user-service', 'com/aicsp/user/controller/UserController.java', 'UserController', 'PUT', '/api/users/{userId}/avatar', 'updateAvatar', 'Update user avatar', TRUE),
  (2006, 'user-service:GET:/api/users/{userId}/roles', 'user-service', 'com/aicsp/user/controller/UserController.java', 'UserController', 'GET', '/api/users/{userId}/roles', 'userRoles', 'List user roles', TRUE),
  (2007, 'user-service:PUT:/api/users/{userId}/roles', 'user-service', 'com/aicsp/user/controller/UserController.java', 'UserController', 'PUT', '/api/users/{userId}/roles', 'assignUserRoles', 'Assign user roles', TRUE),
  (2010, 'user-service:GET:/api/roles', 'user-service', 'com/aicsp/user/controller/RoleController.java', 'RoleController', 'GET', '/api/roles', 'listRoles', 'List roles', TRUE),
  (2011, 'user-service:POST:/api/roles', 'user-service', 'com/aicsp/user/controller/RoleController.java', 'RoleController', 'POST', '/api/roles', 'createRole', 'Create role', TRUE),
  (2020, 'user-service:GET:/api/permissions', 'user-service', 'com/aicsp/user/controller/PermissionController.java', 'PermissionController', 'GET', '/api/permissions', 'listPermissions', 'List permissions', TRUE),
  (2030, 'user-service:GET:/api/resources', 'user-service', 'com/aicsp/user/controller/ResourceController.java', 'ResourceController', 'GET', '/api/resources', 'list', 'List API resources', TRUE),
  (2031, 'user-service:GET:/api/resources/tree', 'user-service', 'com/aicsp/user/controller/ResourceController.java', 'ResourceController', 'GET', '/api/resources/tree', 'tree', 'API resource tree', TRUE),
  (2032, 'user-service:POST:/api/resources/sync', 'user-service', 'com/aicsp/user/controller/ResourceController.java', 'ResourceController', 'POST', '/api/resources/sync', 'sync', 'Sync API resources', TRUE),
  (2033, 'user-service:GET:/api/resources/roles/{roleId}', 'user-service', 'com/aicsp/user/controller/ResourceController.java', 'ResourceController', 'GET', '/api/resources/roles/{roleId}', 'roleResources', 'List role resources', TRUE),
  (2034, 'user-service:PUT:/api/resources/roles/{roleId}', 'user-service', 'com/aicsp/user/controller/ResourceController.java', 'ResourceController', 'PUT', '/api/resources/roles/{roleId}', 'assignRoleResources', 'Assign role resources', TRUE),
  (2040, 'biz-service:GET:/api/sessions', 'biz-service', 'com/aicsp/biz/controller/SessionController.java', 'SessionController', 'GET', '/api/sessions', 'listSessions', 'List sessions', TRUE),
  (2041, 'biz-service:POST:/api/sessions', 'biz-service', 'com/aicsp/biz/controller/SessionController.java', 'SessionController', 'POST', '/api/sessions', 'createSession', 'Create session', TRUE),
  (2042, 'biz-service:GET:/api/messages', 'biz-service', 'com/aicsp/biz/controller/MessageController.java', 'MessageController', 'GET', '/api/messages', 'listMessages', 'List messages', TRUE)
ON CONFLICT DO NOTHING;

UPDATE cs_user_role SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE user_id = 'U1001' AND role_id = 1001 AND deleted = FALSE;

INSERT INTO cs_user_role (id, user_id, role_id, created_by, updated_by)
SELECT 900000100001, 'U1001', 1000, 0, 0
WHERE EXISTS (SELECT 1 FROM cs_user WHERE user_id = 'U1001' AND deleted = FALSE)
  AND NOT EXISTS (SELECT 1 FROM cs_user_role WHERE user_id = 'U1001' AND role_id = 1000 AND deleted = FALSE)
ON CONFLICT DO NOTHING;

UPDATE cs_role_resource SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE role_id IN (1000, 1001, 1002) AND deleted = FALSE;

INSERT INTO cs_role_resource (id, role_id, resource_id, created_by, updated_by)
SELECT 900001000000 + ROW_NUMBER() OVER (ORDER BY ar.id), 1000, ar.id, 0, 0
FROM cs_api_resource ar
WHERE ar.deleted = FALSE
ON CONFLICT DO NOTHING;

INSERT INTO cs_role_resource (id, role_id, resource_id, created_by, updated_by)
SELECT 900002000000 + ROW_NUMBER() OVER (ORDER BY ar.id), 1001, ar.id, 0, 0
FROM cs_api_resource ar
WHERE ar.deleted = FALSE
  AND ar.resource_code IN (
    'user-service:GET:/api/users',
    'user-service:POST:/api/users',
    'user-service:GET:/api/users/{userId}',
    'user-service:PUT:/api/users/{userId}',
    'user-service:PUT:/api/users/{userId}/avatar',
    'user-service:GET:/api/roles',
    'user-service:POST:/api/roles',
    'user-service:GET:/api/permissions',
    'user-service:GET:/api/resources',
    'user-service:GET:/api/resources/tree',
    'user-service:POST:/api/resources/sync',
    'biz-service:GET:/api/sessions',
    'biz-service:POST:/api/sessions',
    'biz-service:GET:/api/messages'
  )
ON CONFLICT DO NOTHING;

INSERT INTO cs_role_resource (id, role_id, resource_id, created_by, updated_by)
SELECT 900003000000 + ROW_NUMBER() OVER (ORDER BY ar.id), 1002, ar.id, 0, 0
FROM cs_api_resource ar
WHERE ar.deleted = FALSE
  AND ar.resource_code IN (
    'user-service:GET:/api/users/{userId}',
    'user-service:PUT:/api/users/{userId}',
    'user-service:PUT:/api/users/{userId}/avatar',
    'biz-service:GET:/api/sessions',
    'biz-service:POST:/api/sessions',
    'biz-service:GET:/api/messages'
  )
ON CONFLICT DO NOTHING;
