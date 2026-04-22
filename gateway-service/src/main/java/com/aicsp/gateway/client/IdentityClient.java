package com.aicsp.gateway.client;

import com.aicsp.gateway.dto.ForwardHeadersDto;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class IdentityClient {

    public Mono<ForwardHeadersDto> introspect(String authorizationHeader, String traceId) {
        String userId = authorizationHeader == null || authorizationHeader.isBlank() ? "anonymous" : "placeholder-user";
        return Mono.just(new ForwardHeadersDto(traceId, userId, "default-tenant", "USER"));
    }
}
