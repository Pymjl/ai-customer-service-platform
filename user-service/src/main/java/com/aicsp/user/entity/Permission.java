package com.aicsp.user.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    private Long id;
    private String permissionCode;
    private String permissionName;
    private LocalDateTime createdAt;
}
