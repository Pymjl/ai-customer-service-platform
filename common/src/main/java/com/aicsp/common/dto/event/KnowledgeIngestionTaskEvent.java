package com.aicsp.common.dto.event;

import lombok.Data;

@Data
public class KnowledgeIngestionTaskEvent {

    private String taskId;
    private String taskType;
    private String documentId;
    private String kbId;
    private Integer kbVersion;
    private String kbType;
    private String kbName;
    private String tenantId;
    private String scope;
    private String ownerUserId;
    private String objectPath;
    private String title;
    private String sourceType;
    private String categoryId;
    private String productLine;
    private Boolean enabled;
    private String traceId;
}
