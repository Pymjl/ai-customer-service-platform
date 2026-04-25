package com.aicsp.user.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9]{1,100}$", message = "账户名仅支持字母和数字，长度不超过100")
    private String username;
    @NotBlank
    @Size(max = 100)
    private String password;
    @NotBlank
    private String captchaToken;
}
