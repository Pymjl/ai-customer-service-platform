package com.aicsp.biz.entity;

import java.time.OffsetDateTime;
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
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}
