package com.aicsp.user.service.impl;

import com.aicsp.common.util.DistributedIdUtils;
import com.aicsp.user.entity.Role;
import com.aicsp.user.mapper.RoleMapper;
import com.aicsp.user.mapper.UserRoleMapper;
import com.aicsp.user.service.RbacService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacServiceImpl implements RbacService {
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public RbacServiceImpl(UserRoleMapper userRoleMapper, RoleMapper roleMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    @Transactional
    public void assignUserRoles(String userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        if (roleIds != null) {
            roleIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .filter(this::isEnabledRole)
                    .forEach(roleId -> userRoleMapper.insert(DistributedIdUtils.nextId(), userId, roleId));
        }
    }

    @Override
    public List<Long> userRoleIds(String userId) {
        return roleMapper.selectByUserId(userId).stream().map(Role::getId).toList();
    }

    private boolean isEnabledRole(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        return role != null && Boolean.TRUE.equals(role.getEnabled());
    }
}
