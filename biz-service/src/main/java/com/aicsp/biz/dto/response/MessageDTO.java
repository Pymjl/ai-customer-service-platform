package com.aicsp.biz.dto.response;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDTO {

    private String messageId;
    private String sessionId;
    private String userMsg;
    private String aiReply;
    private String status;
    private String traceId;
    private OffsetDateTime createdAt;
}
