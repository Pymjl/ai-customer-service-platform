package com.aicsp.biz.dto.knowledge;

import lombok.Data;

@Data
public class ContributionReviewRequest {

    private String comment;
    private Boolean activateVersion;
}
