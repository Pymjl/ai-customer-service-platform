package com.aicsp.user.service.impl;

import com.aicsp.common.util.DistributedIdUtils;
import com.aicsp.user.mapper.UserRoleMapper;
import com.aicsp.user.service.RbacService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacServiceImpl implements RbacService {
    private final UserRoleMapper userRoleMapper;

    public RbacServiceImpl(UserRoleMapper userRoleMapper) {
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    @Transactional
    public void assignUserRoles(String userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        if (roleIds != null) {
            roleIds.forEach(roleId -> userRoleMapper.insert(DistributedIdUtils.nextId(), userId, roleId));
        }
    }

    @Override
    public List<Long> userRoleIds(String userId) {
        return userRoleMapper.selectRoleIdsByUserId(userId);
    }
}
