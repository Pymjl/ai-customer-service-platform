package com.aicsp.biz.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeBaseRequest {

    @NotBlank(message = "scope 不能为空")
    private String scope;

    @NotBlank(message = "name 不能为空")
    private String name;

    private String description;
    private String kbType;
    private Boolean enabled;
}
