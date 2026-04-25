package com.aicsp.user.service.impl;

import com.aicsp.common.util.DistributedIdUtils;
import com.aicsp.user.dto.request.RoleCreateRequest;
import com.aicsp.user.dto.response.RoleDTO;
import com.aicsp.user.entity.Role;
import com.aicsp.user.mapper.RoleMapper;
import com.aicsp.user.mapper.converter.RoleConverter;
import com.aicsp.user.service.RoleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {
    private final RoleMapper roleMapper;
    private final RoleConverter roleConverter;

    @Override
    public List<RoleDTO> listRoles() {
        return roleMapper.selectAll().stream().map(role -> RoleDTO.builder().id(role.getId()).roleCode(role.getRoleCode()).roleName(role.getRoleName()).build()).toList();
    }

    @Override
    public void createRole(RoleCreateRequest request) {
        if (roleMapper.selectByCode(request.getRoleCode()) != null) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        Role role = roleConverter.toEntity(request);
        role.setId(DistributedIdUtils.nextId());
        roleMapper.insert(role);
    }
}
