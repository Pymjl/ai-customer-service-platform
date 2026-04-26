package com.aicsp.user.service.impl;

import com.aicsp.user.dto.resource.ApiResourceDTO;
import com.aicsp.user.entity.ApiResource;
import com.aicsp.user.entity.Role;
import com.aicsp.user.mapper.ApiResourceMapper;
import com.aicsp.user.mapper.RoleMapper;
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
        RoleMapper roleMapper = Mockito.mock(RoleMapper.class);
        Mockito.when(mapper.selectAll()).thenReturn(List.of(resource));
        ResourceServiceImpl service = new ResourceServiceImpl(mapper, roleMapper, ".");

        List<ApiResourceDTO> list = service.listResources();

        Assertions.assertEquals(1, list.size());
        Assertions.assertEquals("user-service", service.tree().getFirst().getLabel());
        Assertions.assertEquals("GET", list.getFirst().getHttpMethod());
    }

    @Test
    void shouldAssignRoleResources() {
        ApiResourceMapper mapper = Mockito.mock(ApiResourceMapper.class);
        RoleMapper roleMapper = Mockito.mock(RoleMapper.class);
        Role role = new Role();
        role.setId(1L);
        role.setRoleCode("ADMIN");
        role.setEnabled(true);
        ApiResource resource2 = new ApiResource();
        resource2.setId(2L);
        resource2.setEnabled(true);
        ApiResource resource3 = new ApiResource();
        resource3.setId(3L);
        resource3.setEnabled(true);
        Mockito.when(roleMapper.selectById(1L)).thenReturn(role);
        Mockito.when(mapper.selectById(2L)).thenReturn(resource2);
        Mockito.when(mapper.selectById(3L)).thenReturn(resource3);
        ResourceServiceImpl service = new ResourceServiceImpl(mapper, roleMapper, ".");

        service.assignRoleResources(1L, List.of(2L, 3L));

        Mockito.verify(mapper).deleteRoleResources(1L);
        Mockito.verify(mapper, Mockito.times(2)).insertRoleResource(Mockito.anyLong(), Mockito.eq(1L), Mockito.anyLong());
    }
}
