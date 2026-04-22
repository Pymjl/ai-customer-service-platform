package com.aicsp.stream.service;

import com.aicsp.stream.dto.request.ChatRequest;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Flux;

public interface ChatStreamService {

    Flux<ServerSentEvent<String>> stream(ChatRequest request, ServerHttpRequest httpRequest);
}
