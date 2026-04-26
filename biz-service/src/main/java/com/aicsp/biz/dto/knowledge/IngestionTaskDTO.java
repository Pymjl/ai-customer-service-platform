package com.aicsp.biz.dto.knowledge;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class IngestionTaskDTO {

    private String documentId;
    private String taskId;
    private String status;
    private Integer progress;
    private Integer chunkCount;
    private String embeddingModel;
    private String errorMessage;
    private OffsetDateTime updatedAt;
}
