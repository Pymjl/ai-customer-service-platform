package com.aicsp.biz.entity;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class KnowledgeIngestionTask {

    private String taskId;
    private String documentId;
    private String kbId;
    private Integer kbVersion;
    private String taskType;
    private String status;
    private Integer progress;
    private Integer chunkCount;
    private String embeddingModel;
    private Integer retryCount;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
