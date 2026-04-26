package com.aicsp.stream.dto;

public record ErrorResponse(String code, String message, String traceId, Integer upstreamStatus) {
}
