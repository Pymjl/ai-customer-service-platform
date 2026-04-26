package com.aicsp.biz.security;

import java.util.List;

public record RequestIdentity(String userId, String tenantId, List<String> roles) {

    public boolean hasAnyRole(String... expectedRoles) {
        for (String role : roles) {
            for (String expectedRole : expectedRoles) {
                if (role.equalsIgnoreCase(expectedRole) || role.equalsIgnoreCase("ROLE_" + expectedRole)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isAdmin() {
        return hasAnyRole("ADMIN", "SUPER_ADMIN", "管理员", "超级管理员");
    }

    public boolean isSuperAdmin() {
        return hasAnyRole("SUPER_ADMIN", "超级管理员");
    }
}
