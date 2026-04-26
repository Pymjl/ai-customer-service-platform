package com.aicsp.user.controller;

import com.aicsp.common.result.R;
import com.aicsp.user.dto.rbac.AssignRoleResourcesRequest;
import com.aicsp.user.dto.resource.ApiResourceDTO;
import com.aicsp.user.dto.resource.ResourceSyncResponse;
import com.aicsp.user.dto.resource.ResourceTreeNode;
import com.aicsp.user.service.ResourceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {
    private final ResourceService resourceService;

    /**
     * 用途：查询 API 资源平铺列表。
     *
     * @return API 资源列表，包含资源编码、服务、Controller、方法、路径、描述和启用状态
     */
    @GetMapping
    public R<List<ApiResourceDTO>> list() {
        return R.ok(resourceService.listResources());
    }

    /**
     * 用途：查询 API 资源树。
     *
     * @return 按服务、包目录、Controller 和 API 组织的资源树
     */
    @GetMapping("/tree")
    public R<List<ResourceTreeNode>> tree() {
        return R.ok(resourceService.tree());
    }

    /**
     * 用途：扫描各服务 Controller 并同步 API 资源描述。
     *
     * @return 同步结果，包含扫描数量、新增数量和更新数量
     */
    @PostMapping("/sync")
    public R<ResourceSyncResponse> sync() {
        return R.ok(resourceService.sync());
    }

    /**
     * 用途：更新 API 资源启用状态。
     *
     * @param id API 资源 ID
     * @param request 资源更新请求，当前使用 enabled 字段控制启用或停用
     * @return 空结果，表示更新成功
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody ApiResourceDTO request) {
        resourceService.updateResource(id, request);
        return R.ok();
    }

    /**
     * 用途：查询指定角色已授权的启用 API 资源。
     *
     * @param roleId 角色 ID
     * @return 资源 ID 列表，仅包含启用资源
     */
    @GetMapping("/roles/{roleId}")
    public R<List<Long>> roleResources(@PathVariable("roleId") Long roleId) {
        return R.ok(resourceService.roleResourceIds(roleId));
    }

    /**
     * 用途：为指定角色分配 API 资源，停用角色和停用资源不参与授权。
     *
     * @param roleId 角色 ID
     * @param request 角色资源授权请求，包含资源 ID 列表
     * @return 空结果，表示保存成功
     */
    @PutMapping("/roles/{roleId}")
    public R<Void> assignRoleResources(@PathVariable("roleId") Long roleId, @Valid @RequestBody AssignRoleResourcesRequest request) {
        resourceService.assignRoleResources(roleId, request.getResourceIds());
        return R.ok();
    }
}
