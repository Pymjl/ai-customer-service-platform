package com.aicsp.biz.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeCategoryRequest {

    @NotBlank(message = "scope 不能为空")
    private String scope;
    private String parentId;
    @NotBlank(message = "name 不能为空")
    private String name;
    private Integer sortOrder;
}
