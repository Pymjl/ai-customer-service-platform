package com.aicsp.gateway.filter;

import com.aicsp.common.constant.HeaderConstants;
import com.aicsp.common.result.R;
import com.aicsp.common.result.ResultCode;
import com.aicsp.common.util.JsonUtils;
import com.aicsp.gateway.client.IdentityClient;
import com.aicsp.gateway.config.GatewayModuleProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    private final IdentityClient identityClient;
    private final GatewayModuleProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;

    public AuthGlobalFilter(IdentityClient identityClient, GatewayModuleProperties properties, ReactiveStringRedisTemplate redisTemplate) {
        this.identityClient = identityClient;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (properties.getWhitelistPrefixes().stream().anyMatch(path::startsWith)) {
            return rateLimit(exchange, chain, "anonymous");
        }
        if (path.startsWith("/internal/")) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return write(exchange, HttpStatus.UNAUTHORIZED, R.fail(ResultCode.UNAUTHORIZED));
        }
        return identityClient.introspect(authorization).flatMap(profile -> {
            if (!Boolean.TRUE.equals(profile.getActive())) {
                return write(exchange, HttpStatus.UNAUTHORIZED, R.fail(ResultCode.UNAUTHORIZED));
            }
            return identityClient.authorize(authorization, exchange.getRequest().getMethod().name(), path).flatMap(allowed -> {
                if (!allowed) {
                    return write(exchange, HttpStatus.FORBIDDEN, R.fail(ResultCode.FORBIDDEN));
                }
                ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
                    headers.remove(HeaderConstants.X_USER_ID);
                    headers.remove(HeaderConstants.X_TENANT_ID);
                    headers.remove(HeaderConstants.X_USER_ROLES);
                    headers.remove(HeaderConstants.X_INTERNAL_TOKEN);
                    headers.set(HeaderConstants.X_USER_ID, profile.getUserId());
                    headers.set(HeaderConstants.X_TENANT_ID, profile.getTenantId());
                    headers.set(HeaderConstants.X_USER_ROLES, profile.getRoles());
                    headers.set(HeaderConstants.X_INTERNAL_TOKEN, properties.getInternalToken());
                }).build();
                return rateLimit(exchange.mutate().request(request).build(), chain, profile.getUserId());
            });
        });
    }

    private Mono<Void> rateLimit(ServerWebExchange exchange, GatewayFilterChain chain, String principal) {
        String bucket = String.valueOf(System.currentTimeMillis() / 60000);
        String key = "gateway:rate:" + principal + ":" + exchange.getRequest().getPath().value() + ":" + bucket;
        return redisTemplate.opsForValue().increment(key).flatMap(count -> redisTemplate.expire(key, Duration.ofMinutes(2)).thenReturn(count))
                .flatMap(count -> count > properties.getRateLimitPerMinute() ? write(exchange, HttpStatus.TOO_MANY_REQUESTS, R.fail(429, "too many requests")) : chain.filter(exchange))
                .doOnError(exception -> log.error("Gateway request failed, method={}, path={}, principal={}",
                        exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(), principal, exception));
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, R<?> body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] payload = JsonUtils.toJson(body).getBytes(StandardCharsets.UTF_8);
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(payload);
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
