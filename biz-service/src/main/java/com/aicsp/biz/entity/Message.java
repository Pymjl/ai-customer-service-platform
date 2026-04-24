package com.aicsp.biz.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private Long id;
    private String messageId;
    private String sessionId;
    private String userId;
    private String tenantId;
    private String userMsg;
    private String aiReply;
    private String status;
    private String traceId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private Boolean deleted;
    private LocalDateTime deletedAt;
}
