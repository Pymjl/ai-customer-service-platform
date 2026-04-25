package com.aicsp.user.entity;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long id;
    private String userId;
    private String tenantId;
    private String username;
    private String password;
    private String avatarPath;
    private Integer gender;
    private String realName;
    private Integer age;
    private String email;
    private String phone;
    private String address;
    private Integer status;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}
