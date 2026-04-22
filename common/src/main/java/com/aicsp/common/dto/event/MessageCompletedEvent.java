package com.aicsp.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageCompletedEvent {

    private String messageId;
    private String sessionId;
    private String userId;
    private String tenantId;
    private String userMessage;
    private String aiReply;
    private String status;
    private String traceId;
    private Long timestamp;
}
