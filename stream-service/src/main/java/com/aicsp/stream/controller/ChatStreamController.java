package com.aicsp.stream.controller;

import com.aicsp.common.exception.BizException;
import com.aicsp.common.result.R;
import com.aicsp.common.result.ResultCode;
import com.aicsp.common.util.JsonUtils;
import com.aicsp.stream.dto.request.ChatRequest;
import com.aicsp.stream.service.ChatStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
public class ChatStreamController {

    private final ChatStreamService chatStreamService;

    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest request, ServerHttpRequest httpRequest) {
        return chatStreamService.stream(request, httpRequest)
                .onErrorResume(BizException.class, e -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(JsonUtils.toJson(R.fail(e.getCode(), e.getBizMessage())))
                        .build()))
                .onErrorResume(Exception.class, e -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(JsonUtils.toJson(R.fail(ResultCode.SYSTEM_ERROR)))
                        .build()));
    }
}
