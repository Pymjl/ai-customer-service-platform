package com.aicsp.biz.entity;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class KnowledgeContributionApplication {

    private Long id;
    private String applicationId;
    private String tenantId;
    private String applicationType;
    private String sourceDocumentId;
    private String sourceKbId;
    private String sourceSnapshotId;
    private String targetKbId;
    private Integer targetVersionNo;
    private String applicantUserId;
    private String status;
    private String targetCategoryId;
    private String reason;
    private String rejectReason;
    private String reviewComment;
    private String reviewerUserId;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}
