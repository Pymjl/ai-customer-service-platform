package com.aicsp.biz.entity;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class KnowledgeBase {

    private Long id;
    private String kbId;
    private String tenantId;
    private String scope;
    private String ownerUserId;
    private String name;
    private String description;
    private String kbType;
    private String sourceKbId;
    private Integer sourceVersion;
    private Integer currentVersion;
    private Boolean enabled;
    private String status;
    private Boolean locked;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
    private Long documentCount;
}
