package com.aicsp.biz.dto.knowledge;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeDocumentUpdateRequest {

    private String title;
    private String sourceType;
    private String categoryId;
    private String productLine;
    private List<String> tags;
}
