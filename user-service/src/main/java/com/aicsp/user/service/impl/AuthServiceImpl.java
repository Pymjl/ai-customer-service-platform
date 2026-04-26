package com.aicsp.user.service.impl;

import com.aicsp.common.util.DistributedIdUtils;
import com.aicsp.user.config.JwtProperties;
import com.aicsp.user.dto.auth.IntrospectionResponse;
import com.aicsp.user.dto.auth.LoginRequest;
import com.aicsp.user.dto.auth.LogoutRequest;
import com.aicsp.user.dto.auth.RefreshTokenRequest;
import com.aicsp.user.dto.auth.RegisterRequest;
import com.aicsp.user.dto.auth.TokenResponse;
import com.aicsp.user.dto.auth.UserProfile;
import com.aicsp.user.entity.ApiResource;
import com.aicsp.user.entity.Role;
import com.aicsp.user.entity.User;
import com.aicsp.user.mapper.ApiResourceMapper;
import com.aicsp.user.mapper.RoleMapper;
import com.aicsp.user.mapper.UserMapper;
import com.aicsp.user.mapper.UserRoleMapper;
import com.aicsp.user.security.JwtTokenService;
import com.aicsp.user.security.RefreshTokenService;
import com.aicsp.user.service.AuthService;
import com.aicsp.user.service.CaptchaService;
import com.aicsp.user.service.FileStorageService;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    private static final String ADMIN_ROLE = "ADMIN";
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final ApiResourceMapper apiResourceMapper;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;
    private final FileStorageService fileStorageService;

    public AuthServiceImpl(UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper, ApiResourceMapper apiResourceMapper, PasswordEncoder passwordEncoder, CaptchaService captchaService, JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService, JwtProperties jwtProperties, FileStorageService fileStorageService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.apiResourceMapper = apiResourceMapper;
        this.passwordEncoder = passwordEncoder;
        this.captchaService = captchaService;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        captchaService.consumeToken(request.getCaptchaToken());
        String username = normalizeUsername(request.getUsername());
        User user = userMapper.selectByUsername("default", username);
        if (user == null || user.getStatus() == null || user.getStatus() != 1 || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("账户名或密码错误");
        }
        List<String> roles = activeRoles(user.getUserId()).stream().map(Role::getRoleCode).toList();
        return issueToken(user, roles);
    }

    @Override
    public TokenResponse refresh(RefreshTokenRequest request) {
        String userId = refreshTokenService.consume(request.getRefreshToken());
        User user = userMapper.selectByUserId(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            refreshTokenService.revokeAll(userId);
            throw new IllegalArgumentException("invalid refresh token");
        }
        List<String> roles = activeRoles(user.getUserId()).stream().map(Role::getRoleCode).toList();
        return issueToken(user, roles);
    }

    @Override
    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.getRefreshToken());
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        register(request, null);
    }

    @Override
    @Transactional
    public void register(RegisterRequest request, MultipartFile avatar) {
        captchaService.consumeToken(request.getCaptchaToken());
        String tenantId = request.getTenantId() == null || request.getTenantId().isBlank() ? "default" : request.getTenantId();
        String username = normalizeUsername(request.getUsername());
        if (userMapper.selectByUsername(tenantId, username) != null) {
            throw new IllegalArgumentException("账户名已存在");
        }
        User user = new User();
        user.setId(DistributedIdUtils.nextId());
        user.setUserId("U" + user.getId());
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setGender(request.getGender());
        user.setRealName(request.getRealName().trim());
        user.setAge(request.getAge());
        user.setEmail(trimToNull(request.getEmail()));
        user.setPhone(trimToNull(request.getPhone()));
        user.setAddress(trimToNull(request.getAddress()));
        user.setAvatarPath(fileStorageService.uploadAvatar(user.getUserId(), avatar));
        user.setStatus(1);
        userMapper.insert(user);
        Role role = roleMapper.selectByCode("USER");
        if (role != null && Boolean.TRUE.equals(role.getEnabled())) {
            userRoleMapper.insert(DistributedIdUtils.nextId(), user.getUserId(), role.getId());
        }
    }

    @Override
    public UserProfile me(String authorization) {
        JwtTokenService.TokenClaims claims = parseHeader(authorization);
        User user = userMapper.selectByUserId(claims.userId());
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        List<String> roles = splitRoles(claims.roles());
        return toProfile(user, roles);
    }

    @Override
    public IntrospectionResponse introspect(String authorization) {
        IntrospectionResponse response = new IntrospectionResponse();
        try {
            JwtTokenService.TokenClaims claims = parseHeader(authorization);
            response.setActive(true);
            response.setUserId(claims.userId());
            response.setTenantId(claims.tenantId());
            response.setUsername(claims.username());
            response.setRoles(claims.roles());
        } catch (Exception e) {
            response.setActive(false);
        }
        return response;
    }

    @Override
    public boolean authorize(String authorization, String httpMethod, String path) {
        JwtTokenService.TokenClaims claims = parseHeader(authorization);
        List<Role> activeRoles = activeRoles(claims.userId());
        List<String> roles = activeRoles.stream().map(Role::getRoleCode).toList();
        String normalizedPath = path == null ? "" : path.split("\\?")[0];
        if (matchesDisabledResource(httpMethod, normalizedPath)) {
            return false;
        }
        if (roles.contains(SUPER_ADMIN_ROLE)) {
            return true;
        }
        List<Long> roleIds = activeRoles.stream().map(Role::getId).toList();
        if (roleIds.isEmpty()) {
            return false;
        }
        return apiResourceMapper.selectByRoleIds(roleIds).stream()
                .filter(ApiResource::getEnabled)
                .anyMatch(resource -> resource.getHttpMethod().equalsIgnoreCase(httpMethod)
                        && matchPath(resource.getPath(), normalizedPath)
                        && matchesScope(resource, normalizedPath, claims.userId(), roles));
    }

    private JwtTokenService.TokenClaims parseHeader(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("未登录");
        }
        return jwtTokenService.parse(authorization.substring(7));
    }

    private List<String> splitRoles(String roles) {
        return roles == null || roles.isBlank() ? List.of() : List.of(roles.split(","));
    }

    private List<Role> activeRoles(String userId) {
        return roleMapper.selectByUserId(userId);
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private UserProfile toProfile(User user, List<String> roles) {
        return new UserProfile(user.getUserId(), user.getTenantId(), user.getUsername(), user.getAvatarPath(), user.getGender(), user.getRealName(), user.getAge(), user.getEmail(), user.getPhone(), user.getAddress(), roles);
    }

    private TokenResponse issueToken(User user, List<String> roles) {
        String accessToken = jwtTokenService.createToken(user.getUserId(), user.getTenantId(), user.getUsername(), roles);
        String refreshToken = refreshTokenService.create(user.getUserId());
        return new TokenResponse(accessToken, refreshToken, "Bearer", jwtProperties.getTtlSeconds(), toProfile(user, roles));
    }

    private boolean matchPath(String pattern, String path) {
        if (pattern.equals(path)) {
            return true;
        }
        String regex = pattern.replaceAll("\\{[^/]+}", "[^/]+");
        return path.matches(regex);
    }

    private boolean matchesDisabledResource(String httpMethod, String path) {
        return apiResourceMapper.selectAll().stream()
                .filter(resource -> !Boolean.TRUE.equals(resource.getEnabled()))
                .anyMatch(resource -> resource.getHttpMethod().equalsIgnoreCase(httpMethod)
                        && matchPath(resource.getPath(), path));
    }

    private boolean matchesScope(ApiResource resource, String path, String currentUserId, List<String> roles) {
        if (roles.contains(ADMIN_ROLE)) {
            return true;
        }
        if (!resource.getPath().startsWith("/api/users/{userId}")) {
            return true;
        }
        String prefix = "/api/users/";
        if (!path.startsWith(prefix)) {
            return false;
        }
        String targetUserId = path.substring(prefix.length()).split("/")[0];
        return currentUserId.equals(targetUserId);
    }
}
