package com.aicsp.user.service.impl;

import com.aicsp.user.dto.response.PermissionDTO;
import com.aicsp.user.mapper.PermissionMapper;
import com.aicsp.user.mapper.converter.PermissionConverter;
import com.aicsp.user.service.PermissionService;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;
    private final PermissionConverter permissionConverter;

    @Override
    public List<PermissionDTO> listPermissions() {
        return Collections.emptyList();
    }
}
