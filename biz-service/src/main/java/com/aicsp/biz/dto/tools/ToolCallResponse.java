package com.aicsp.biz.dto.tools;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolCallResponse {

    private String toolCallId;
    private Boolean success;
    private Map<String, Object> data;
    private String errorCode;
    private String message;
}
