package com.aicsp.biz.dto.knowledge;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContributionDiffDTO {

    private String applicationId;
    private String sourceSnapshotId;
    private String targetKbId;
    private Integer targetVersionNo;
    private List<Item> added;
    private List<Item> modified;
    private List<Item> deleted;

    @Data
    @Builder
    public static class Item {
        private String sourceDocumentId;
        private String targetDocumentId;
        private String title;
        private String sourceType;
        private String fingerprint;
        private String previousFingerprint;
    }
}
