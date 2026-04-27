package com.aicsp.biz.dto.knowledge;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContributionApplicationDTO {

    private String applicationId;
    private String applicationType;
    private String sourceKbId;
    private String targetKbId;
    private String sourceSnapshotId;
    private String status;
    private String reason;
    private String reviewComment;
    private OffsetDateTime createdAt;
    private OffsetDateTime reviewedAt;
}
