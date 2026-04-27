package com.aicsp.biz.dto.knowledge;

import lombok.Data;

@Data
public class ContributionRequest {

    private String reason;
    private String targetKbId;
}
