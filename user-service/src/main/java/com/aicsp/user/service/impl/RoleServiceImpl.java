package com.aicsp.user.service.impl;

import com.aicsp.common.util.DistributedIdUtils;
import com.aicsp.user.dto.request.RoleCreateRequest;
import com.aicsp.user.dto.response.RoleDTO;
import com.aicsp.user.entity.Role;
import com.aicsp.user.mapper.RoleMapper;
import com.aicsp.user.mapper.converter.RoleConverter;
import com.aicsp.user.service.RoleService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {
    private final RoleMapper roleMapper;
    private final RoleConverter roleConverter;

    @Override
    public List<RoleDTO> listRoles() {
        return roleMapper.selectAll().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public void createRole(RoleCreateRequest request) {
        if (roleMapper.selectByCode(request.getRoleCode()) != null) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        Role role = roleConverter.toEntity(request);
        role.setId(DistributedIdUtils.nextId());
        role.setDescription(normalizeDescription(role.getDescription()));
        role.setEnabled(role.getEnabled() == null || role.getEnabled());
        roleMapper.insert(role);
    }

    @Override
    @Transactional
    public void updateRole(Long id, RoleCreateRequest request) {
        Role exists = roleMapper.selectById(id);
        if (exists == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        Role sameCode = roleMapper.selectByCode(request.getRoleCode());
        if (sameCode != null && !Objects.equals(sameCode.getId(), id)) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        exists.setRoleCode(request.getRoleCode());
        exists.setRoleName(request.getRoleName());
        exists.setDescription(normalizeDescription(request.getDescription()));
        exists.setEnabled(request.getEnabled() == null || request.getEnabled());
        roleMapper.update(exists);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        roleMapper.deleteById(id);
    }

    private RoleDTO toDto(Role role) {
        return RoleDTO.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .enabled(role.getEnabled() == null || role.getEnabled())
                .build();
    }

    private String normalizeDescription(String description) {
        return description == null ? "" : description.trim();
    }
}
