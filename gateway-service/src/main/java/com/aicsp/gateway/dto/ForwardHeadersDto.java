package com.aicsp.gateway.dto;

public record ForwardHeadersDto(String traceId, String userId, String tenantId, String roles) {
}
