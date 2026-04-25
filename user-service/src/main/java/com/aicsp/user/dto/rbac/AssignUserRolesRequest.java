package com.aicsp.user.dto.rbac;

import java.util.List;
import lombok.Data;

@Data
public class AssignUserRolesRequest {
    private List<Long> roleIds;
}
