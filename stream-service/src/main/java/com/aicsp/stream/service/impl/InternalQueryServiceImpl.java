package com.aicsp.stream.service.impl;

import com.aicsp.stream.service.InternalQueryService;
import com.aicsp.stream.service.internal.CrmQueryService;
import com.aicsp.stream.service.internal.LogisticsQueryService;
import com.aicsp.stream.service.internal.OrderQueryService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class InternalQueryServiceImpl implements InternalQueryService {

    private final OrderQueryService orderQueryService;
    private final LogisticsQueryService logisticsQueryService;
    private final CrmQueryService crmQueryService;

    @Override
    public Mono<Map<String, Object>> query(String functionName, Map<String, String> params) {
        return switch (functionName) {
            case "order" -> orderQueryService.query(params);
            case "logistics" -> logisticsQueryService.query(params);
            case "crm" -> crmQueryService.query(params);
            default -> Mono.just(Map.of("function", functionName, "params", params, "message", "unsupported function"));
        };
    }
}
