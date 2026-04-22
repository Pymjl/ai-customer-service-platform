package com.aicsp.stream.config;

import com.aicsp.common.constant.HeaderConstants;
import com.aicsp.common.exception.ErrorCode;
import com.aicsp.common.result.R;
import com.aicsp.common.util.JsonUtils;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                .build();
    }

    @Bean
    public WebFilter internalTokenWebFilter(@Value("${internal.token:}") String internalToken) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().value();
            if (!path.startsWith("/internal/")) {
                return chain.filter(exchange);
            }
            String provided = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_INTERNAL_TOKEN);
            if (internalToken != null && !internalToken.isBlank() && internalToken.equals(provided)) {
                return chain.filter(exchange);
            }
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] payload = JsonUtils.toJson(R.fail(ErrorCode.INTERNAL_TOKEN_INVALID.getCode(),
                    ErrorCode.INTERNAL_TOKEN_INVALID.getMessage())).getBytes(StandardCharsets.UTF_8);
            DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(payload);
            return exchange.getResponse().writeWith(Mono.just(dataBuffer));
        };
    }
}
