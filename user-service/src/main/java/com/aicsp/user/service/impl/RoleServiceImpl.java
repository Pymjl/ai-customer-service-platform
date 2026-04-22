package com.aicsp.user.service.impl;

import com.aicsp.user.dto.request.RoleCreateRequest;
import com.aicsp.user.dto.response.RoleDTO;
import com.aicsp.user.mapper.RoleMapper;
import com.aicsp.user.mapper.converter.RoleConverter;
import com.aicsp.user.service.RoleService;
import java.util.Collections;
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
        return Collections.emptyList();
    }

    @Override
    public void createRole(RoleCreateRequest request) {
        roleConverter.toEntity(request);
    }
}
