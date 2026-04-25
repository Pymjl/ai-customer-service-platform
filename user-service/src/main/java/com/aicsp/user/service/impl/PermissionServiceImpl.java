package com.aicsp.user.service.impl;

import com.aicsp.user.dto.response.PermissionDTO;
import com.aicsp.user.mapper.PermissionMapper;
import com.aicsp.user.service.PermissionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final PermissionMapper permissionMapper;

    @Override
    public List<PermissionDTO> listPermissions() {
        return permissionMapper.selectAll().stream()
                .map(permission -> PermissionDTO.builder().id(permission.getId()).permissionCode(permission.getPermissionCode()).permissionName(permission.getPermissionName()).build())
                .toList();
    }
}
