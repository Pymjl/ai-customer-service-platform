package com.aicsp.user.dto.resource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResourceDTO {
    private Long id;
    private String resourceCode;
    private String serviceName;
    private String controllerPath;
    private String controllerName;
    private String httpMethod;
    private String path;
    private String methodName;
    private String description;
    private String requestExample;
    private String responseExample;
    private Boolean enabled;
}
