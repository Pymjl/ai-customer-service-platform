package com.aicsp.stream.filter;

import com.aicsp.stream.config.StreamModuleProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalTokenWebFilterTest {

    @Test
    void rejectsInternalRequestWhenConfiguredTokenIsBlank() {
        StreamModuleProperties properties = new StreamModuleProperties();
        properties.setInternalToken("");
        InternalTokenWebFilter filter = new InternalTokenWebFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/tasks").header("X-Internal-Token", ""));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, currentExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void allowsInternalRequestWhenTokenMatches() {
        StreamModuleProperties properties = new StreamModuleProperties();
        properties.setInternalToken("prod-secret");
        InternalTokenWebFilter filter = new InternalTokenWebFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/tasks").header("X-Internal-Token", "prod-secret"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, currentExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertTrue(chainCalled.get());
    }
}
