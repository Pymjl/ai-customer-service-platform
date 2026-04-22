package com.aicsp.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AssignRoleRequest {

    @NotBlank(message = "用户 ID 不能为空")
    private String userId;

    @NotNull(message = "角色 ID 不能为空")
    private Long roleId;
}
