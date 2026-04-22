package com.aicsp.biz.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SessionCreateRequest {

    @NotBlank(message = "sessionId 不能为空")
    private String sessionId;

    @NotBlank(message = "userId 不能为空")
    private String userId;

    @NotBlank(message = "tenantId 不能为空")
    private String tenantId;

    private String title;
}
