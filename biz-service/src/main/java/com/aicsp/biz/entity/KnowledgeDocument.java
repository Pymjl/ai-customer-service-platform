package com.aicsp.biz.entity;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class KnowledgeDocument {

    private Long id;
    private String documentId;
    private String tenantId;
    private String scope;
    private String ownerUserId;
    private String title;
    private String sourceType;
    private String objectPath;
    private String categoryId;
    private String productLine;
    private String status;
    private Boolean enabled;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}
