package com.aicsp.biz.dto.knowledge;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class KnowledgeBaseDTO {

    private String kbId;
    private String scope;
    private String name;
    private String description;
    private String kbType;
    private String sourceKbId;
    private Integer sourceVersion;
    private Integer currentVersion;
    private Boolean enabled;
    private String status;
    private Boolean locked;
    private Long documentCount;
    private Boolean manageable;
    private Boolean hasPublicSnapshot;
    private String publicSnapshotKbId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
