package com.aicsp.user.entity;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class ApiResource {
    private Long id;
    private String resourceCode;
    private String serviceName;
    private String controllerPath;
    private String controllerName;
    private String httpMethod;
    private String path;
    private String methodName;
    private String description;
    private Boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}
