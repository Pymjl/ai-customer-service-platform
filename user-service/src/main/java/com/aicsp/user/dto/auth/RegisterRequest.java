package com.aicsp.user.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9]{1,100}$", message = "账户名仅支持字母和数字，长度不超过100")
    private String username;
    @NotBlank
    @Size(min = 6, max = 100)
    private String password;
    @NotBlank
    private String captchaToken;
    @Size(max = 64)
    private String tenantId = "default";
    @NotNull
    @Min(0)
    @Max(2)
    private Integer gender;
    @NotBlank
    @Pattern(regexp = "^[\\p{L}\\p{N}_\\-\\s]{1,50}$", message = "昵称仅支持中英文、数字、空格、下划线和短横线，长度不超过50")
    private String realName;
    @NotNull
    @Min(1)
    @Max(120)
    private Integer age;
    @Size(max = 128)
    @Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "邮箱格式不正确")
    private String email;
    @Size(max = 32)
    @Pattern(regexp = "^$|^\\+?[0-9][0-9\\-\\s]{5,30}$", message = "电话号码格式不正确")
    private String phone;
    @Size(max = 256)
    private String address;
}
