package com.aicsp.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {

    private String userId;
    private String username;
    private String tenantId;
    private String avatarPath;
    private Integer gender;
    private String realName;
    private Integer age;
    private String email;
    private String phone;
    private String address;
    private Integer status;
}
