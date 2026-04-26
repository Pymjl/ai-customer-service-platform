package com.aicsp.biz.dto.knowledge;

import lombok.Data;

@Data
public class IngestionCallbackRequest {

    private String taskId;
    private String documentId;
    private String status;
    private Integer progress;
    private Integer chunkCount;
    private String embeddingModel;
    private String errorMessage;
    private String traceId;
}
