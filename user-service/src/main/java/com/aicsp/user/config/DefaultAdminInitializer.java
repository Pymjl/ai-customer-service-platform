package com.aicsp.user.config;

import com.aicsp.common.util.DistributedIdUtils;
import com.aicsp.user.entity.Role;
import com.aicsp.user.entity.User;
import com.aicsp.user.mapper.RoleMapper;
import com.aicsp.user.mapper.UserMapper;
import com.aicsp.user.mapper.UserRoleMapper;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DefaultAdminInitializer implements ApplicationRunner {
    private static final String ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final AdminProperties adminProperties;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public DefaultAdminInitializer(AdminProperties adminProperties, UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder) {
        this.adminProperties = adminProperties;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        hashUnsafePasswords();
        Role adminRole = ensureAdminRole();
        User adminUser = ensureAdminUser();
        ensureAdminRoleBinding(adminUser, adminRole);
    }

    private void hashUnsafePasswords() {
        userMapper.selectAll().forEach(user -> {
            String securePassword = toSecurePassword(user.getPassword());
            if (securePassword != null) {
                userMapper.updatePassword(user.getUserId(), securePassword);
            }
        });
    }

    private Role ensureAdminRole() {
        Role role = roleMapper.selectByCode(ADMIN_ROLE_CODE);
        if (role != null) {
            return role;
        }
        Role adminRole = new Role();
        adminRole.setId(DistributedIdUtils.nextId());
        adminRole.setRoleCode(ADMIN_ROLE_CODE);
        adminRole.setRoleName("超级管理员");
        adminRole.setDescription("拥有系统全部管理和授权权限的内置角色");
        adminRole.setEnabled(true);
        roleMapper.insert(adminRole);
        return adminRole;
    }

    private User ensureAdminUser() {
        User user = userMapper.selectByUsername(adminProperties.getTenantId(), adminProperties.getUsername());
        String encodedPassword = passwordEncoder.encode(adminProperties.getPassword());
        if (user != null) {
            if (shouldResetPassword(user.getPassword())) {
                userMapper.updatePassword(user.getUserId(), encodedPassword);
            }
            return user;
        }
        User adminUser = new User();
        adminUser.setId(DistributedIdUtils.nextId());
        adminUser.setUserId("U" + adminUser.getId());
        adminUser.setTenantId(adminProperties.getTenantId());
        adminUser.setUsername(adminProperties.getUsername());
        adminUser.setPassword(encodedPassword);
        adminUser.setGender(adminProperties.getGender());
        adminUser.setRealName(adminProperties.getRealName());
        adminUser.setAge(adminProperties.getAge());
        adminUser.setStatus(1);
        userMapper.insert(adminUser);
        return adminUser;
    }

    private void ensureAdminRoleBinding(User user, Role role) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(user.getUserId());
        if (!roleIds.contains(role.getId())) {
            userRoleMapper.insert(DistributedIdUtils.nextId(), user.getUserId(), role.getId());
        }
    }

    private boolean shouldResetPassword(String password) {
        if (password == null || password.isBlank() || password.startsWith("{noop}")) {
            return true;
        }
        try {
            return !passwordEncoder.matches(adminProperties.getPassword(), password);
        } catch (IllegalArgumentException ex) {
            return true;
        }
    }

    private String toSecurePassword(String password) {
        if (password == null || password.isBlank()) {
            return null;
        }
        if (password.startsWith("{noop}")) {
            return passwordEncoder.encode(password.substring("{noop}".length()));
        }
        if (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$")) {
            return "{bcrypt}" + password;
        }
        if (!password.startsWith("{")) {
            return passwordEncoder.encode(password);
        }
        return null;
    }
}
