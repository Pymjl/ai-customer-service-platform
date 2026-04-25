package com.aicsp.gateway.dto;

import lombok.Data;

@Data
public class IntrospectionResponse {
    private Boolean active;
    private String userId;
    private String tenantId;
    private String username;
    private String roles;
}
