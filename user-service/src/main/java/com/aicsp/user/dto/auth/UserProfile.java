package com.aicsp.user.dto.auth;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserProfile {
    private String userId;
    private String tenantId;
    private String username;
    private String avatarPath;
    private Integer gender;
    private String realName;
    private Integer age;
    private String email;
    private String phone;
    private String address;
    private List<String> roles;
}
