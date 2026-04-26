package com.aicsp.stream.service.impl;

import com.aicsp.common.constant.HeaderConstants;
import com.aicsp.common.util.TraceIdUtils;
import com.aicsp.stream.client.PythonEngineClient;
import com.aicsp.stream.dto.engine.EngineEvent;
import com.aicsp.stream.dto.engine.EngineRequest;
import com.aicsp.stream.dto.request.ChatRequest;
import com.aicsp.stream.service.ChatStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ChatStreamServiceImpl implements ChatStreamService {

    private final PythonEngineClient pythonEngineClient;

    @Override
    public Flux<ServerSentEvent<String>> stream(ChatRequest request, ServerHttpRequest httpRequest) {
        String traceId = httpRequest.getHeaders().getFirst(HeaderConstants.X_TRACE_ID);
        String resolvedTraceId = traceId == null || traceId.isBlank() ? TraceIdUtils.generate() : traceId;
        EngineRequest engineRequest = EngineRequest.builder()
                .sessionId(request.getSessionId())
                .message(request.getMessage())
                .traceId(resolvedTraceId)
                .build();

        return pythonEngineClient.stream(engineRequest)
                .map(this::toServerSentEvent);
    }

    private ServerSentEvent<String> toServerSentEvent(EngineEvent event) {
        return ServerSentEvent.builder(event.getData())
                .event(event.getEvent())
                .build();
    }
}
