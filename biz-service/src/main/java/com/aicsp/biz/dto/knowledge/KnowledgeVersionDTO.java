package com.aicsp.biz.dto.knowledge;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class KnowledgeVersionDTO {

    private String versionId;
    private String kbId;
    private Integer versionNo;
    private String versionType;
    private String sourceKbId;
    private Integer sourceVersionNo;
    private String note;
    private OffsetDateTime createdAt;
}
