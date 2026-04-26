package com.aicsp.stream.dto.engine;

import com.aicsp.stream.dto.request.ChatRequest;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineRequest {

    private String messageId;
    private String sessionId;
    private String message;
    private String traceId;
    private String userId;
    private String tenantId;
    private List<String> roles;
    private String locale;
    private ChatRequest.KnowledgeSelection knowledgeSelection;
    private Map<String, Object> metadata;
}
