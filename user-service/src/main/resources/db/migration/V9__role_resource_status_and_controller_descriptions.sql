ALTER TABLE cs_role ADD COLUMN IF NOT EXISTS description VARCHAR(256) NOT NULL DEFAULT '';
ALTER TABLE cs_role ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE cs_role
SET role_name = U&'\8D85\7EA7\7BA1\7406\5458',
    description = U&'\62E5\6709\7CFB\7EDF\5168\90E8\7BA1\7406\548C\6388\6743\6743\9650\7684\5185\7F6E\89D2\8272',
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE role_code = 'SUPER_ADMIN' AND deleted = FALSE;

UPDATE cs_role
SET role_name = U&'\7BA1\7406\5458',
    description = U&'\8D1F\8D23\65E5\5E38\7528\6237\3001\89D2\8272\3001\8D44\6E90\548C\4E1A\52A1\7BA1\7406',
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE role_code = 'ADMIN' AND deleted = FALSE;

UPDATE cs_role
SET role_name = U&'\666E\901A\7528\6237',
    description = U&'\666E\901A\4E1A\52A1\4F7F\7528\8005\FF0C\53EF\8BBF\95EE\667A\80FD\5BA2\670D\548C\672C\4EBA\8D44\6599',
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE role_code = 'USER' AND deleted = FALSE;

INSERT INTO cs_api_resource (id, resource_code, service_name, controller_path, controller_name, http_method, path, method_name, description, enabled)
VALUES
  (2012, 'user-service:PUT:/api/roles/{id}', 'user-service', 'com/aicsp/user/controller/RoleController.java', 'RoleController', 'PUT', '/api/roles/{id}', 'updateRole', U&'\7528\9014\FF1A\66F4\65B0\89D2\8272\57FA\7840\4FE1\606F\548C\542F\7528\72B6\6001\000A\53C2\6570\FF1Aid\FF1A\89D2\8272 ID\FF1Brequest\FF1A\89D2\8272\66F4\65B0\8BF7\6C42\000A\8FD4\56DE\FF1A\7A7A\7ED3\679C\FF0C\8868\793A\66F4\65B0\6210\529F', TRUE),
  (2013, 'user-service:DELETE:/api/roles/{id}', 'user-service', 'com/aicsp/user/controller/RoleController.java', 'RoleController', 'DELETE', '/api/roles/{id}', 'deleteRole', U&'\7528\9014\FF1A\903B\8F91\5220\9664\89D2\8272\000A\53C2\6570\FF1Aid\FF1A\89D2\8272 ID\000A\8FD4\56DE\FF1A\7A7A\7ED3\679C\FF0C\8868\793A\5220\9664\6210\529F', TRUE),
  (2035, 'user-service:PUT:/api/resources/{id}', 'user-service', 'com/aicsp/user/controller/ResourceController.java', 'ResourceController', 'PUT', '/api/resources/{id}', 'update', U&'\7528\9014\FF1A\66F4\65B0 API \8D44\6E90\542F\7528\72B6\6001\000A\53C2\6570\FF1Aid\FF1AAPI \8D44\6E90 ID\FF1Brequest\FF1A\4F7F\7528 enabled \5B57\6BB5\63A7\5236\542F\7528\6216\505C\7528\000A\8FD4\56DE\FF1A\7A7A\7ED3\679C\FF0C\8868\793A\66F4\65B0\6210\529F', TRUE),
  (2050, 'user-service:GET:/api/auth/captcha', 'user-service', 'com/aicsp/user/controller/AuthController.java', 'AuthController', 'GET', '/api/auth/captcha', 'captcha', U&'\7528\9014\FF1A\521B\5EFA\6ED1\5757\9A8C\8BC1\7801\6311\6218\000A\8FD4\56DE\FF1A\9A8C\8BC1\7801\6311\6218\4FE1\606F', TRUE),
  (2051, 'user-service:POST:/api/auth/captcha/verify', 'user-service', 'com/aicsp/user/controller/AuthController.java', 'AuthController', 'POST', '/api/auth/captcha/verify', 'verifyCaptcha', U&'\7528\9014\FF1A\6821\9A8C\6ED1\5757\9A8C\8BC1\7801\4F4D\7F6E\000A\53C2\6570\FF1Arequest\FF1A\6311\6218 ID \548C\6ED1\5757\6A2A\5411\4F4D\7F6E\000A\8FD4\56DE\FF1A\9A8C\8BC1\7ED3\679C\548C captchaToken', TRUE),
  (2052, 'user-service:POST:/api/auth/register', 'user-service', 'com/aicsp/user/controller/AuthController.java', 'AuthController', 'POST', '/api/auth/register', 'register', U&'\7528\9014\FF1A\6CE8\518C\666E\901A\7528\6237\8D26\53F7\000A\53C2\6570\FF1Arequest\FF1A\6CE8\518C\8BF7\6C42\000A\8FD4\56DE\FF1A\7A7A\7ED3\679C\FF0C\8868\793A\6CE8\518C\6210\529F', TRUE),
  (2053, 'user-service:POST:/api/auth/register-with-avatar', 'user-service', 'com/aicsp/user/controller/AuthController.java', 'AuthController', 'POST', '/api/auth/register-with-avatar', 'registerWithAvatar', U&'\7528\9014\FF1A\6CE8\518C\666E\901A\7528\6237\5E76\4E0A\4F20\5934\50CF\000A\53C2\6570\FF1Arequest\FF1A\6CE8\518C\8BF7\6C42\FF1Bavatar\FF1A\5934\50CF\6587\4EF6\000A\8FD4\56DE\FF1A\7A7A\7ED3\679C\FF0C\8868\793A\6CE8\518C\6210\529F', TRUE),
  (2054, 'user-service:POST:/api/auth/login', 'user-service', 'com/aicsp/user/controller/AuthController.java', 'AuthController', 'POST', '/api/auth/login', 'login', U&'\7528\9014\FF1A\7528\6237\767B\5F55\5E76\7B7E\53D1\4EE4\724C\000A\53C2\6570\FF1Arequest\FF1A\767B\5F55\8BF7\6C42\000A\8FD4\56DE\FF1AaccessToken\3001refreshToken\548C\7528\6237\8D44\6599', TRUE),
  (2055, 'user-service:POST:/api/auth/refresh', 'user-service', 'com/aicsp/user/controller/AuthController.java', 'AuthController', 'POST', '/api/auth/refresh', 'refresh', U&'\7528\9014\FF1A\4F7F\7528\5237\65B0\4EE4\724C\6362\53D6\65B0\8BBF\95EE\4EE4\724C\000A\53C2\6570\FF1Arequest\FF1ArefreshToken\000A\8FD4\56DE\FF1A\65B0\7684\4EE4\724C\7ED3\679C', TRUE),
  (2056, 'user-service:POST:/api/auth/logout', 'user-service', 'com/aicsp/user/controller/AuthController.java', 'AuthController', 'POST', '/api/auth/logout', 'logout', U&'\7528\9014\FF1A\6CE8\9500\5F53\524D\4F1A\8BDD\5E76\540A\9500\5237\65B0\4EE4\724C\000A\53C2\6570\FF1Arequest\FF1ArefreshToken\000A\8FD4\56DE\FF1A\7A7A\7ED3\679C\FF0C\8868\793A\6CE8\9500\6210\529F', TRUE),
  (2057, 'user-service:GET:/api/auth/me', 'user-service', 'com/aicsp/user/controller/AuthController.java', 'AuthController', 'GET', '/api/auth/me', 'me', U&'\7528\9014\FF1A\67E5\8BE2\5F53\524D\767B\5F55\7528\6237\8D44\6599\000A\53C2\6570\FF1Aauthorization\FF1ABearer \8BBF\95EE\4EE4\724C\000A\8FD4\56DE\FF1A\5F53\524D\7528\6237\8D44\6599', TRUE),
  (2058, 'user-service:POST:/api/auth/authorize', 'user-service', 'com/aicsp/user/controller/AuthController.java', 'AuthController', 'POST', '/api/auth/authorize', 'authorize', U&'\7528\9014\FF1A\7F51\5173\8C03\7528\7684\63A5\53E3\9274\6743\000A\53C2\6570\FF1Aauthorization\FF1ABearer \8BBF\95EE\4EE4\724C\FF1Brequest\FF1AHTTP \65B9\6CD5\548C\8BF7\6C42\8DEF\5F84\000A\8FD4\56DE\FF1Aallowed \8868\793A\662F\5426\5141\8BB8\8BBF\95EE', TRUE),
  (2059, 'user-service:POST:/api/auth/introspect', 'user-service', 'com/aicsp/user/controller/AuthController.java', 'AuthController', 'POST', '/api/auth/introspect', 'introspect', U&'\7528\9014\FF1A\89E3\6790\5E76\68C0\67E5\8BBF\95EE\4EE4\724C\72B6\6001\000A\53C2\6570\FF1Aauthorization\FF1ABearer \8BBF\95EE\4EE4\724C\000A\8FD4\56DE\FF1A\4EE4\724C\81EA\7701\7ED3\679C', TRUE),
  (2060, 'stream-service:POST:/api/chat/stream', 'stream-service', 'com/aicsp/stream/controller/ChatStreamController.java', 'ChatStreamController', 'POST', '/api/chat/stream', 'stream', U&'\7528\9014\FF1A\53D1\8D77\667A\80FD\5BA2\670D\6D41\5F0F\5BF9\8BDD\000A\53C2\6570\FF1Arequest\FF1A\804A\5929\8BF7\6C42\FF1BhttpRequest\FF1AHTTP \8BF7\6C42\000A\8FD4\56DE\FF1ASSE \4E8B\4EF6\6D41', TRUE),
  (2061, 'stream-service:GET:/internal/{functionName}', 'stream-service', 'com/aicsp/stream/controller/InternalController.java', 'InternalController', 'GET', '/internal/{functionName}', 'query', U&'\7528\9014\FF1A\6267\884C\5185\90E8\51FD\6570\67E5\8BE2\000A\53C2\6570\FF1AfunctionName\FF1A\5185\90E8\51FD\6570\540D\79F0\FF1Bparams\FF1A\67E5\8BE2\53C2\6570\000A\8FD4\56DE\FF1A\67E5\8BE2\7ED3\679C Map', TRUE),
  (2062, 'gateway-service:GET:/gateway/ping', 'gateway-service', 'com/aicsp/gateway/controller/GatewayProbeController.java', 'GatewayProbeController', 'GET', '/gateway/ping', 'ping', U&'\7528\9014\FF1A\7F51\5173\5065\5EB7\63A2\6D4B\000A\8FD4\56DE\FF1A\670D\52A1\540D\79F0\548C ok \72B6\6001', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO cs_role_resource (id, role_id, resource_id, created_by, updated_by)
SELECT 900004000000 + ROW_NUMBER() OVER (ORDER BY ar.id), r.id, ar.id, 0, 0
FROM cs_api_resource ar
JOIN cs_role r ON r.role_code = 'SUPER_ADMIN' AND r.deleted = FALSE
WHERE ar.deleted = FALSE
  AND ar.enabled = TRUE
  AND NOT EXISTS (
    SELECT 1 FROM cs_role_resource rr WHERE rr.role_id = r.id AND rr.resource_id = ar.id AND rr.deleted = FALSE
  )
ON CONFLICT DO NOTHING;

INSERT INTO cs_role_resource (id, role_id, resource_id, created_by, updated_by)
SELECT 900005000000 + ROW_NUMBER() OVER (ORDER BY ar.id), r.id, ar.id, 0, 0
FROM cs_api_resource ar
JOIN cs_role r ON r.role_code = 'ADMIN' AND r.deleted = FALSE
WHERE ar.deleted = FALSE
  AND ar.enabled = TRUE
  AND ar.resource_code IN (
    'user-service:GET:/api/users/{userId}/roles',
    'user-service:PUT:/api/users/{userId}/roles',
    'user-service:PUT:/api/roles/{id}',
    'user-service:DELETE:/api/roles/{id}',
    'user-service:PUT:/api/resources/{id}',
    'user-service:GET:/api/resources/roles/{roleId}',
    'user-service:PUT:/api/resources/roles/{roleId}'
  )
  AND NOT EXISTS (
    SELECT 1 FROM cs_role_resource rr WHERE rr.role_id = r.id AND rr.resource_id = ar.id AND rr.deleted = FALSE
  )
ON CONFLICT DO NOTHING;
