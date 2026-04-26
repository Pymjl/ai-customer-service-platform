package com.aicsp.biz.dto.knowledge;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

@Data
public class KnowledgeDocumentDTO {

    private String documentId;
    private String scope;
    private String title;
    private String sourceType;
    private String objectPath;
    private String categoryId;
    private String productLine;
    private String status;
    private Boolean enabled;
    private List<String> tags;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
