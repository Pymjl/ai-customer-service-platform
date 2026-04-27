package com.aicsp.biz.dto.knowledge;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievalFilterResponse {

    private String mode;
    private Boolean skipRetrieval;
    private String tenantId;
    private List<String> allowedKbIds;
    private List<String> allowedScopes;
    private Map<String, Object> filters;
    private List<DeniedCandidate> deniedCandidates;

    @Data
    @Builder
    public static class DeniedCandidate {
        private String kbIdHash;
        private String documentIdHash;
        private String scope;
        private String reason;
    }
}
