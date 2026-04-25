package com.aicsp.user.dto.auth;

import lombok.Data;

@Data
public class IntrospectionResponse {
    private boolean active;
    private String userId;
    private String tenantId;
    private String username;
    private String roles;
}
