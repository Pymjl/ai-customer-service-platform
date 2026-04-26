package com.aicsp.biz.dto.tools;

import java.util.Map;
import lombok.Data;

@Data
public class ToolCallRequest {

    private String toolCallId;
    private String sessionId;
    private String messageId;
    private String userId;
    private String tenantId;
    private Map<String, Object> arguments;
    private String idempotencyKey;
}
