package com.aicsp.biz.entity;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class KnowledgeSnapshotDocument {

    private Long id;
    private String snapshotId;
    private String sourceKbId;
    private Integer sourceVersion;
    private String sourceDocumentId;
    private String title;
    private String sourceType;
    private String objectPath;
    private String categoryId;
    private String productLine;
    private Boolean enabled;
    private String status;
    private String fingerprint;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
    private Boolean deleted;
}
