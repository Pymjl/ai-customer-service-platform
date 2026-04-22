package com.aicsp.user.service;

import com.aicsp.user.dto.response.PermissionDTO;
import java.util.List;

public interface PermissionService {

    List<PermissionDTO> listPermissions();
}
