package com.aicsp.user.service;

import java.util.List;

public interface RbacService {
    void assignUserRoles(String userId, List<Long> roleIds);
    List<Long> userRoleIds(String userId);
}
