package com.aicsp.stream.service.impl;

import com.aicsp.common.constant.HeaderConstants;
import com.aicsp.common.util.JsonUtils;
import com.aicsp.common.util.TraceIdUtils;
import com.aicsp.stream.config.StreamSseProperties;
import com.aicsp.stream.client.PythonEngineClient;
import com.aicsp.stream.dto.engine.EngineEvent;
import com.aicsp.stream.dto.engine.EngineRequest;
import com.aicsp.stream.dto.request.ChatRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.aicsp.stream.service.ChatStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ChatStreamServiceImpl implements ChatStreamService {

    private final PythonEngineClient pythonEngineClient;
    private final StreamSseProperties streamSseProperties;

    @Override
    public Flux<ServerSentEvent<String>> stream(ChatRequest request, ServerHttpRequest httpRequest) {
        String traceId = httpRequest.getHeaders().getFirst(HeaderConstants.X_TRACE_ID);
        String resolvedTraceId = traceId == null || traceId.isBlank() ? TraceIdUtils.generate() : traceId;
        EngineRequest engineRequest = EngineRequest.builder()
                .messageId(resolveMessageId(request.getMessageId()))
                .sessionId(request.getSessionId())
                .message(request.getMessage())
                .traceId(resolvedTraceId)
                .userId(httpRequest.getHeaders().getFirst(HeaderConstants.X_USER_ID))
                .tenantId(httpRequest.getHeaders().getFirst(HeaderConstants.X_TENANT_ID))
                .roles(parseRoles(httpRequest.getHeaders().getFirst(HeaderConstants.X_USER_ROLES)))
                .locale(resolveLocale(request.getLocale()))
                .knowledgeSelection(request.getKnowledgeSelection())
                .metadata(Collections.emptyMap())
                .build();

        return withHeartbeatAndMaxDuration(pythonEngineClient.stream(engineRequest)
                .map(this::toServerSentEvent));
    }

    private ServerSentEvent<String> toServerSentEvent(EngineEvent event) {
        return ServerSentEvent.builder(event.getData())
                .event(event.getEvent())
                .build();
    }

    private Flux<ServerSentEvent<String>> withHeartbeatAndMaxDuration(Flux<ServerSentEvent<String>> upstream) {
        Duration heartbeatInterval = parseDuration(streamSseProperties.getHeartbeatInterval());
        Duration maxDuration = parseDuration(streamSseProperties.getMaxDuration());
        return upstream.publish(shared -> Flux.merge(
                        shared,
                        Flux.interval(heartbeatInterval)
                                .map(ignored -> ServerSentEvent.<String>builder()
                                        .event("heartbeat")
                                        .data(JsonUtils.toJson(Map.of("ts", System.currentTimeMillis())))
                                        .build())
                                .takeUntilOther(shared.then())
                ))
                .take(maxDuration);
    }

    private String resolveMessageId(String messageId) {
        return messageId == null || messageId.isBlank() ? UUID.randomUUID().toString() : messageId;
    }

    private String resolveLocale(String locale) {
        return locale == null || locale.isBlank() ? "zh-CN" : locale;
    }

    private List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toList();
    }

    private Duration parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ZERO;
        }
        return DurationStyle.detectAndParse(value.trim());
    }
}
