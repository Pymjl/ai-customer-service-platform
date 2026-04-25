package com.aicsp.user.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthorizeRequest {
    @NotBlank
    private String httpMethod;
    @NotBlank
    private String path;
}
