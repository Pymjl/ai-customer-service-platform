package com.aicsp.user.service;

import com.aicsp.user.dto.request.RoleCreateRequest;
import com.aicsp.user.dto.response.RoleDTO;
import java.util.List;

public interface RoleService {

    List<RoleDTO> listRoles();

    void createRole(RoleCreateRequest request);

    void updateRole(Long id, RoleCreateRequest request);

    void deleteRole(Long id);
}
