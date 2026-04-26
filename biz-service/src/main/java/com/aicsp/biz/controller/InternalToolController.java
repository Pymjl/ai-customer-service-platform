package com.aicsp.biz.controller;

import com.aicsp.biz.dto.tools.ToolCallRequest;
import com.aicsp.biz.dto.tools.ToolCallResponse;
import com.aicsp.common.result.R;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/tools")
@RequiredArgsConstructor
public class InternalToolController {

    @PostMapping("/{toolName}")
    public R<ToolCallResponse> callTool(@PathVariable String toolName, @RequestBody ToolCallRequest request) {
        ToolCallResponse response = switch (toolName) {
            case "order.query" -> ToolCallResponse.builder()
                    .toolCallId(request.getToolCallId())
                    .success(true)
                    .data(Map.of("status", "UNKNOWN", "message", "订单系统尚未接入，已进入人工核验"))
                    .message("success")
                    .build();
            case "logistics.query" -> ToolCallResponse.builder()
                    .toolCallId(request.getToolCallId())
                    .success(true)
                    .data(Map.of("status", "UNKNOWN", "message", "物流系统尚未接入，已进入人工核验"))
                    .message("success")
                    .build();
            case "crm.query" -> ToolCallResponse.builder()
                    .toolCallId(request.getToolCallId())
                    .success(true)
                    .data(Map.of("status", "MASKED", "message", "CRM 系统尚未接入，未返回敏感客户信息"))
                    .message("success")
                    .build();
            default -> ToolCallResponse.builder()
                    .toolCallId(request.getToolCallId())
                    .success(false)
                    .errorCode("TOOL_NOT_FOUND")
                    .message("工具不存在")
                    .build();
        };
        return R.ok(response);
    }
}
