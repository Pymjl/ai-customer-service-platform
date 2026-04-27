package com.aicsp.stream.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatRequest {

    private String messageId;

    @NotBlank(message = "sessionId 不能为空")
    private String sessionId;

    @NotBlank(message = "message 不能为空")
    private String message;

    private String locale;

    private KnowledgeSelection knowledgeSelection;

    @Data
    @NoArgsConstructor
    public static class KnowledgeSelection {
        private String mode;
        private Boolean includePublic;
        private Boolean includePersonal;
        private List<String> personalKbIds;
        private List<String> kbIds;
        private List<String> documentIds;
        private List<String> categoryIds;
        private List<String> tagIds;
    }
}
