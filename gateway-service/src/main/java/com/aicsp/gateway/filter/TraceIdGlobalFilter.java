package com.aicsp.gateway.filter;

import com.aicsp.common.constant.HeaderConstants;
import com.aicsp.common.util.TraceIdUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdUtils.generate();
        }
        final String resolvedTraceId = traceId;
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(builder -> builder.headers(headers -> headers.set(HeaderConstants.X_TRACE_ID, resolvedTraceId)))
                .build();
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
