package com.aicsp.biz.entity;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    private Long id;
    private String sessionId;
    private String userId;
    private String tenantId;
    private String title;
    private Integer status;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}
