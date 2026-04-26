package com.aicsp.biz.dto.knowledge;

import java.util.List;
import lombok.Data;

@Data
public class RetrievalFilterRequest {

    private String tenantId;
    private String userId;
    private List<String> roles;
    private KnowledgeSelection knowledgeSelection;
    private String productLine;
    private String traceId;
}
