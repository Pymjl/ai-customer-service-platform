package com.aicsp.user.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {

    private Long id;
    private Long roleId;
    private Long permissionId;
}
