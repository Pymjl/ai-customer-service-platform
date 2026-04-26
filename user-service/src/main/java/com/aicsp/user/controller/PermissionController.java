package com.aicsp.user.controller;

import com.aicsp.common.result.R;
import com.aicsp.user.dto.response.PermissionDTO;
import com.aicsp.user.service.PermissionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 用途：查询权限字典列表。
     *
     * @return 权限列表，包含权限编码和权限名称
     */
    @GetMapping
    public R<List<PermissionDTO>> listPermissions() {
        return R.ok(permissionService.listPermissions());
    }
}
