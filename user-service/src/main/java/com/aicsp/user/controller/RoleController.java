package com.aicsp.user.controller;

import com.aicsp.common.result.R;
import com.aicsp.user.dto.request.RoleCreateRequest;
import com.aicsp.user.dto.response.RoleDTO;
import com.aicsp.user.service.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 用途：查询角色列表。
     *
     * @return 角色列表，包含角色编码、名称、描述和启用状态
     */
    @GetMapping
    public R<List<RoleDTO>> listRoles() {
        return R.ok(roleService.listRoles());
    }

    /**
     * 用途：新增角色。
     *
     * @param request 角色创建请求，包含角色编码、名称、描述和启用状态
     * @return 空结果，表示创建成功
     */
    @PostMapping
    public R<?> createRole(@Valid @RequestBody RoleCreateRequest request) {
        roleService.createRole(request);
        return R.ok();
    }

    /**
     * 用途：更新角色基础信息和启用状态。
     *
     * @param id 角色 ID
     * @param request 角色更新请求，包含角色编码、名称、描述和启用状态
     * @return 空结果，表示更新成功
     */
    @PutMapping("/{id}")
    public R<?> updateRole(@PathVariable("id") Long id, @Valid @RequestBody RoleCreateRequest request) {
        roleService.updateRole(id, request);
        return R.ok();
    }

    /**
     * 用途：逻辑删除角色。
     *
     * @param id 角色 ID
     * @return 空结果，表示删除成功
     */
    @DeleteMapping("/{id}")
    public R<?> deleteRole(@PathVariable("id") Long id) {
        roleService.deleteRole(id);
        return R.ok();
    }
}
