package com.aicsp.gateway.filter;

import com.aicsp.common.constant.HeaderConstants;
import com.aicsp.common.result.R;
import com.aicsp.common.result.ResultCode;
import com.aicsp.common.util.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<String> WHITELIST_PREFIXES = Set.of("/oauth2/", "/actuator/health");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (WHITELIST_PREFIXES.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }
        if (path.startsWith("/internal/")) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HeaderConstants.X_USER_ID);
                    headers.remove(HeaderConstants.X_TENANT_ID);
                    headers.remove(HeaderConstants.X_USER_ROLES);
                    headers.set(HeaderConstants.X_USER_ID, "stub-user");
                    headers.set(HeaderConstants.X_TENANT_ID, "stub-tenant");
                    headers.set(HeaderConstants.X_USER_ROLES, "ROLE_USER");
                })
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] payload = JsonUtils.toJson(R.fail(ResultCode.UNAUTHORIZED)).getBytes(StandardCharsets.UTF_8);
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(payload);
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
