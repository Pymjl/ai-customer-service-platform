package com.aicsp.biz.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeCategoryDTO {

    private String categoryId;
    private String scope;
    private String parentId;
    private String name;
    private Integer sortOrder;
    private Long documentCount;
}
