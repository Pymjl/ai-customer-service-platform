package com.aicsp.biz.entity;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class KnowledgeVersion {

    private Long id;
    private String versionId;
    private String kbId;
    private Integer versionNo;
    private String versionType;
    private String sourceKbId;
    private Integer sourceVersionNo;
    private String note;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Boolean deleted;
}
