package com.aicsp.gateway.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GatewayEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(GatewayEventPublisher.class);

    public void publishRouteAccess(String routeId, String traceId) {
        log.debug("Gateway route access recorded, routeId={}, traceId={}", routeId, traceId);
    }
}
