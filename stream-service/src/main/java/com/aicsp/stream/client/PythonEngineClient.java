package com.aicsp.stream.client;

import com.aicsp.common.constant.HeaderConstants;
import com.aicsp.common.util.JsonUtils;
import com.aicsp.stream.config.PythonEngineProperties;
import com.aicsp.stream.dto.ErrorResponse;
import com.aicsp.stream.dto.engine.EngineEvent;
import com.aicsp.stream.dto.engine.EngineRequest;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PythonEngineClient {

    private static final MediaType APPLICATION_NDJSON = MediaType.parseMediaType("application/x-ndjson");

    private final WebClient pythonEngineWebClient;
    private final PythonEngineProperties properties;

    public Flux<EngineEvent> stream(EngineRequest request) {
        return pythonEngineWebClient.post()
                .uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(APPLICATION_NDJSON)
                .header(HeaderConstants.X_INTERNAL_TOKEN, properties.getInternalToken())
                .headers(headers -> applyIdentityHeaders(headers, request))
                .bodyValue(request)
                .exchangeToFlux(response -> {
                    HttpStatusCode status = response.statusCode();
                    if (status.is2xxSuccessful()) {
                        return response.bodyToFlux(EngineEvent.class);
                    }
                    return response.releaseBody().thenMany(toErrorEvents(request.getTraceId(), status));
                })
                .onErrorResume(e -> Flux.just(errorEvent(
                        "UPSTREAM_STREAM_ERROR",
                        "AI 引擎流式响应异常",
                        request.getTraceId(),
                        null
                ), doneErrorEvent()));
    }

    private void applyIdentityHeaders(HttpHeaders headers, EngineRequest request) {
        setIfPresent(headers, HeaderConstants.X_TRACE_ID, request.getTraceId());
        setIfPresent(headers, HeaderConstants.X_USER_ID, request.getUserId());
        setIfPresent(headers, HeaderConstants.X_TENANT_ID, request.getTenantId());
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            StringJoiner joiner = new StringJoiner(",");
            request.getRoles().forEach(joiner::add);
            setIfPresent(headers, HeaderConstants.X_USER_ROLES, joiner.toString());
        }
    }

    private void setIfPresent(HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }

    private Flux<EngineEvent> toErrorEvents(String traceId, HttpStatusCode status) {
        EngineEvent error = errorEvent(resolveErrorCode(status), resolveErrorMessage(status), traceId, status.value());
        if (status.value() == 401 || status.value() == 403) {
            return Flux.just(error);
        }
        return Flux.concat(Mono.just(error), Mono.just(doneErrorEvent()));
    }

    private EngineEvent errorEvent(String code, String message, String traceId, Integer upstreamStatus) {
        return EngineEvent.builder()
                .event("error")
                .data(JsonUtils.toJson(new ErrorResponse(code, message, traceId, upstreamStatus)))
                .build();
    }

    private EngineEvent doneErrorEvent() {
        return EngineEvent.builder()
                .event("done")
                .data("{\"finishReason\":\"error\"}")
                .build();
    }

    private String resolveErrorCode(HttpStatusCode status) {
        if (status.value() == 401 || status.value() == 403) {
            return "UPSTREAM_UNAUTHORIZED";
        }
        return "UPSTREAM_HTTP_ERROR";
    }

    private String resolveErrorMessage(HttpStatusCode status) {
        if (status.value() == 401 || status.value() == 403) {
            return "AI 引擎内部鉴权失败";
        }
        return "AI 引擎请求失败";
    }
}
