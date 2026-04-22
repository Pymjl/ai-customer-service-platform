package com.aicsp.stream.dto.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineRequest {

    private String sessionId;
    private String message;
    private String traceId;
}
