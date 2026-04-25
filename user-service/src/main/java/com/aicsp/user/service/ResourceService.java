package com.aicsp.user.service;

import com.aicsp.user.dto.resource.ApiResourceDTO;
import com.aicsp.user.dto.resource.ResourceSyncResponse;
import com.aicsp.user.dto.resource.ResourceTreeNode;
import java.util.List;

public interface ResourceService {
    List<ApiResourceDTO> listResources();
    List<ResourceTreeNode> tree();
    ResourceSyncResponse sync();
    void assignRoleResources(Long roleId, List<Long> resourceIds);
    List<Long> roleResourceIds(Long roleId);
}
