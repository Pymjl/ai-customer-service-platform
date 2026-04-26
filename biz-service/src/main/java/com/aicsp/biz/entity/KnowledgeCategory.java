package com.aicsp.biz.entity;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class KnowledgeCategory {

    private Long id;
    private String categoryId;
    private String tenantId;
    private String scope;
    private String ownerUserId;
    private String parentId;
    private String name;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}
