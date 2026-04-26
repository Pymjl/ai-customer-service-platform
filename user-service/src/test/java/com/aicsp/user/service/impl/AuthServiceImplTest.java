package com.aicsp.user.service.impl;

import com.aicsp.user.config.JwtProperties;
import com.aicsp.user.entity.ApiResource;
import com.aicsp.user.entity.Role;
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

    private AuthServiceImpl service(RoleMapper roleMapper, ApiResourceMapper resourceMapper, JwtTokenService jwtTokenService) {
        return new AuthServiceImpl(
                Mockito.mock(UserMapper.class),
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

    private ApiResource resource(Long id, String method, String path) {
        ApiResource resource = new ApiResource();
        resource.setId(id);
        resource.setHttpMethod(method);
        resource.setPath(path);
        resource.setEnabled(true);
        return resource;
    }
}
