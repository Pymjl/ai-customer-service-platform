package com.aicsp.stream.client;

import com.aicsp.stream.dto.engine.EngineEvent;
import com.aicsp.stream.dto.engine.EngineRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class PythonEngineClient {

    private final WebClient pythonEngineWebClient;

    public Flux<EngineEvent> stream(EngineRequest request) {
        return pythonEngineWebClient.post()
                .uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(EngineEvent.class)
                .onErrorResume(e -> Flux.empty());
    }
}
