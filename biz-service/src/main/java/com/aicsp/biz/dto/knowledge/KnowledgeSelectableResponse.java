package com.aicsp.biz.dto.knowledge;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeSelectableResponse {

    private String defaultMode;
    private List<KbOption> publicKbs;
    private List<KbOption> personalKbs;
    private Policy policy;
    private List<ScopeOption> scopes;
    private List<CategoryOption> categories;
    private List<KnowledgeTagDTO> tags;
    private List<DocumentOption> documents;

    @Data
    @Builder
    public static class KbOption {
        private String kbId;
        private String name;
        private String scope;
        private String kbType;
        private Boolean enabled;
        private Boolean locked;
        private Long documentCount;
        private Integer currentVersion;
        private Boolean hasPublicSnapshot;
        private String publicSnapshotKbId;
    }

    @Data
    @Builder
    public static class Policy {
        private Boolean publicAlwaysOn;
        private Boolean personalSelectable;
    }

    @Data
    @Builder
    public static class ScopeOption {
        private String scope;
        private Boolean enabled;
        private Boolean editable;
        private Long documentCount;
    }

    @Data
    @Builder
    public static class CategoryOption {
        private String categoryId;
        private String scope;
        private String name;
        private Long documentCount;
    }

    @Data
    @Builder
    public static class DocumentOption {
        private String documentId;
        private String scope;
        private String title;
        private String status;
        private String categoryId;
        private OffsetDateTime updatedAt;
    }
}
