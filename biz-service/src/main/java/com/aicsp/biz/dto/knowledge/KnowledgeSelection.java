package com.aicsp.biz.dto.knowledge;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeSelection {

    private String mode = "DEFAULT";
    private Boolean includePublic = true;
    private Boolean includePersonal = true;
    private List<String> personalKbIds;
    private List<String> kbIds;
    private List<String> documentIds;
    private List<String> categoryIds;
    private List<String> tagIds;
}
