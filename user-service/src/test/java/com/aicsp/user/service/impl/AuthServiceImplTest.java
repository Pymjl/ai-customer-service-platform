package com.aicsp.user.service.impl;

import com.aicsp.user.config.JwtProperties;
import com.aicsp.user.entity.ApiResource;
import com.aicsp.user.entity.Role;
import com.aicsp.user.entity.User;
import com.aicsp.user.mapper.ApiResourceMapper;
import com.aicsp.user.mapper.RoleMapper;
import com.aicsp.user.mapper.UserMapper;
import com.aicsp.user.mapper.UserRoleMapper;
import com.aicsp.user.security.JwtTokenService;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuthServiceImplTest {

    @Test
    void shouldRecalculatePermissionsWhenOneRoleDisabled() {
        JwtTokenService jwtTokenService = new JwtTokenService(jwtProperties());
        Role role2 = role(2L, "ROLE_2");
        ApiResource permissionA = resource(101L, "GET", "/api/a");
        ApiResource permissionB = resource(102L, "GET", "/api/b");
        ApiResource permissionC = resource(103L, "GET", "/api/c");
        RoleMapper roleMapper = Mockito.mock(RoleMapper.class);
        ApiResourceMapper resourceMapper = Mockito.mock(ApiResourceMapper.class);
        Mockito.when(roleMapper.selectByUserId("U1")).thenReturn(List.of(role2));
        Mockito.when(resourceMapper.selectAll()).thenReturn(List.of(permissionA, permissionB, permissionC));
        Mockito.when(resourceMapper.selectByRoleIds(List.of(2L))).thenReturn(List.of(permissionA, permissionB));
        AuthServiceImpl service = service(roleMapper, resourceMapper, jwtTokenService);
        String token = "Bearer " + jwtTokenService.createToken("U1", "default", "user", List.of("ROLE_1", "ROLE_2"));

        Assertions.assertTrue(service.authorize(token, "GET", "/api/a"));
        Assertions.assertTrue(service.authorize(token, "GET", "/api/b"));
        Assertions.assertFalse(service.authorize(token, "GET", "/api/c"));
    }

    @Test
    void shouldKeepPermissionsWhenAnotherEnabledRoleStillOwnsThem() {
        JwtTokenService jwtTokenService = new JwtTokenService(jwtProperties());
        Role role1 = role(1L, "ROLE_1");
        ApiResource permissionA = resource(101L, "GET", "/api/a");
        ApiResource permissionB = resource(102L, "GET", "/api/b");
        ApiResource permissionC = resource(103L, "GET", "/api/c");
        RoleMapper roleMapper = Mockito.mock(RoleMapper.class);
        ApiResourceMapper resourceMapper = Mockito.mock(ApiResourceMapper.class);
        Mockito.when(roleMapper.selectByUserId("U1")).thenReturn(List.of(role1));
        Mockito.when(resourceMapper.selectAll()).thenReturn(List.of(permissionA, permissionB, permissionC));
        Mockito.when(resourceMapper.selectByRoleIds(List.of(1L))).thenReturn(List.of(permissionA, permissionB, permissionC));
        AuthServiceImpl service = service(roleMapper, resourceMapper, jwtTokenService);
        String token = "Bearer " + jwtTokenService.createToken("U1", "default", "user", List.of("ROLE_1", "ROLE_2"));

        Assertions.assertTrue(service.authorize(token, "GET", "/api/a"));
        Assertions.assertTrue(service.authorize(token, "GET", "/api/b"));
        Assertions.assertTrue(service.authorize(token, "GET", "/api/c"));
    }

    @Test
    void shouldRejectAuthorizationWhenUserDisabled() {
        JwtTokenService jwtTokenService = new JwtTokenService(jwtProperties());
        Role role = role(1L, "ROLE_1");
        ApiResource permission = resource(101L, "GET", "/api/a");
        UserMapper userMapper = Mockito.mock(UserMapper.class);
        RoleMapper roleMapper = Mockito.mock(RoleMapper.class);
        ApiResourceMapper resourceMapper = Mockito.mock(ApiResourceMapper.class);
        Mockito.when(userMapper.selectByUserId("U1")).thenReturn(user("U1", 0));
        Mockito.when(roleMapper.selectByUserId("U1")).thenReturn(List.of(role));
        Mockito.when(resourceMapper.selectAll()).thenReturn(List.of(permission));
        Mockito.when(resourceMapper.selectByRoleIds(List.of(1L))).thenReturn(List.of(permission));
        AuthServiceImpl service = service(userMapper, roleMapper, resourceMapper, jwtTokenService);
        String token = "Bearer " + jwtTokenService.createToken("U1", "default", "user", List.of("ROLE_1"));

        Assertions.assertFalse(service.authorize(token, "GET", "/api/a"));
        Mockito.verifyNoInteractions(roleMapper, resourceMapper);
    }

    @Test
    void shouldReturnInactiveWhenTokenUserMissing() {
        JwtTokenService jwtTokenService = new JwtTokenService(jwtProperties());
        UserMapper userMapper = Mockito.mock(UserMapper.class);
        Mockito.when(userMapper.selectByUserId("U1")).thenReturn(null);
        AuthServiceImpl service = service(userMapper, Mockito.mock(RoleMapper.class), Mockito.mock(ApiResourceMapper.class), jwtTokenService);
        String token = "Bearer " + jwtTokenService.createToken("U1", "default", "user", List.of("ROLE_1"));

        Assertions.assertFalse(service.introspect(token).isActive());
    }

    private AuthServiceImpl service(RoleMapper roleMapper, ApiResourceMapper resourceMapper, JwtTokenService jwtTokenService) {
        UserMapper userMapper = Mockito.mock(UserMapper.class);
        Mockito.when(userMapper.selectByUserId("U1")).thenReturn(user("U1", 1));
        return service(userMapper, roleMapper, resourceMapper, jwtTokenService);
    }

    private AuthServiceImpl service(UserMapper userMapper, RoleMapper roleMapper, ApiResourceMapper resourceMapper, JwtTokenService jwtTokenService) {
        return new AuthServiceImpl(
                userMapper,
                roleMapper,
                Mockito.mock(UserRoleMapper.class),
                resourceMapper,
                null,
                null,
                jwtTokenService,
                null,
                jwtProperties(),
                null);
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("01234567890123456789012345678901");
        properties.setTtlSeconds(900);
        properties.setRefreshTtlSeconds(1800);
        return properties;
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        role.setEnabled(true);
        return role;
    }

    private User user(String userId, Integer status) {
        User user = new User();
        user.setUserId(userId);
        user.setTenantId("default");
        user.setUsername("user");
        user.setStatus(status);
        return user;
    }

    private ApiResource resource(Long id, String method, String path) {
        ApiResource resource = new ApiResource();
        resource.setId(id);
        resource.setHttpMethod(method);
        resource.setPath(path);
        resource.setEnabled(true);
        return resource;
    }
}
