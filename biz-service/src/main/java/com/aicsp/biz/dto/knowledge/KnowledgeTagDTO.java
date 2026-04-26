package com.aicsp.biz.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeTagDTO {

    private String tagId;
    private String name;
    private Long documentCount;
}
