package com.aicsp.stream.dto.response;

import java.util.Map;

public record InternalQueryResponse(String functionName, Map<String, Object> data) {
}
