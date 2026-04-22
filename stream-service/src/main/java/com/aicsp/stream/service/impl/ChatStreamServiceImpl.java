package com.aicsp.stream.service.impl;

import com.aicsp.common.constant.HeaderConstants;
import com.aicsp.common.constant.MessageStatusConstants;
import com.aicsp.common.dto.event.MessageCompletedEvent;
import com.aicsp.common.util.TraceIdUtils;
import com.aicsp.stream.dto.engine.EngineRequest;
import com.aicsp.stream.dto.engine.EngineEvent;
import com.aicsp.stream.dto.request.ChatRequest;
import com.aicsp.stream.publisher.MessageEventPublisher;
import com.aicsp.stream.service.ChatStreamService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ChatStreamServiceImpl implements ChatStreamService {

    private final MessageEventPublisher messageEventPublisher;

    @Override
    public Flux<ServerSentEvent<String>> stream(ChatRequest request, ServerHttpRequest httpRequest) {
        String traceId = httpRequest.getHeaders().getFirst(HeaderConstants.X_TRACE_ID);
        String resolvedTraceId = traceId == null || traceId.isBlank() ? TraceIdUtils.generate() : traceId;
        EngineRequest engineRequest = EngineRequest.builder()
                .sessionId(request.getSessionId())
                .message(request.getMessage())
                .traceId(resolvedTraceId)
                .build();

        EngineEvent event = EngineEvent.builder()
                .event("message")
                .data(engineRequest.getMessage())
                .build();

        MessageCompletedEvent completedEvent = MessageCompletedEvent.builder()
                .messageId(request.getMessageId() == null || request.getMessageId().isBlank()
                        ? resolvedTraceId
                        : request.getMessageId())
                .sessionId(request.getSessionId())
                .userId(httpRequest.getHeaders().getFirst(HeaderConstants.X_USER_ID))
                .tenantId(httpRequest.getHeaders().getFirst(HeaderConstants.X_TENANT_ID))
                .userMessage(request.getMessage())
                .aiReply(request.getMessage())
                .status(MessageStatusConstants.COMPLETED)
                .traceId(resolvedTraceId)
                .timestamp(Instant.now().toEpochMilli())
                .build();

        return Flux.just(ServerSentEvent.builder(event.getData()).event(event.getEvent()).build())
                .concatWith(messageEventPublisher.publishCompleted(completedEvent)
                        .thenMany(Flux.<ServerSentEvent<String>>empty()));
    }
}
