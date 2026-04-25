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

    @GetMapping
    public R<List<ApiResourceDTO>> list() {
        return R.ok(resourceService.listResources());
    }

    @GetMapping("/tree")
    public R<List<ResourceTreeNode>> tree() {
        return R.ok(resourceService.tree());
    }

    @PostMapping("/sync")
    public R<ResourceSyncResponse> sync() {
        return R.ok(resourceService.sync());
    }

    @GetMapping("/roles/{roleId}")
    public R<List<Long>> roleResources(@PathVariable("roleId") Long roleId) {
        return R.ok(resourceService.roleResourceIds(roleId));
    }

    @PutMapping("/roles/{roleId}")
    public R<Void> assignRoleResources(@PathVariable("roleId") Long roleId, @Valid @RequestBody AssignRoleResourcesRequest request) {
        resourceService.assignRoleResources(roleId, request.getResourceIds());
        return R.ok();
    }
}
