package com.aicsp.user.service.impl;

import com.aicsp.user.dto.resource.ApiResourceDTO;
import com.aicsp.user.entity.ApiResource;
import com.aicsp.user.mapper.ApiResourceMapper;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ResourceServiceImplTest {
    @Test
    void shouldBuildResourceTree() {
        ApiResource resource = new ApiResource();
        resource.setId(1L);
        resource.setResourceCode("user-service:GET:/api/users");
        resource.setServiceName("user-service");
        resource.setControllerPath("com/aicsp/user/controller/UserController.java");
        resource.setControllerName("UserController");
        resource.setHttpMethod("GET");
        resource.setPath("/api/users");
        resource.setMethodName("listUsers");
        resource.setDescription("用户列表");
        resource.setEnabled(true);
        ApiResourceMapper mapper = Mockito.mock(ApiResourceMapper.class);
        Mockito.when(mapper.selectAll()).thenReturn(List.of(resource));
        ResourceServiceImpl service = new ResourceServiceImpl(mapper, ".");

        List<ApiResourceDTO> list = service.listResources();

        Assertions.assertEquals(1, list.size());
        Assertions.assertEquals("user-service", service.tree().getFirst().getLabel());
        Assertions.assertEquals("GET", list.getFirst().getHttpMethod());
    }

    @Test
    void shouldAssignRoleResources() {
        ApiResourceMapper mapper = Mockito.mock(ApiResourceMapper.class);
        ResourceServiceImpl service = new ResourceServiceImpl(mapper, ".");

        service.assignRoleResources(1L, List.of(2L, 3L));

        Mockito.verify(mapper).deleteRoleResources(1L);
        Mockito.verify(mapper, Mockito.times(2)).insertRoleResource(Mockito.anyLong(), Mockito.eq(1L), Mockito.anyLong());
    }
}
