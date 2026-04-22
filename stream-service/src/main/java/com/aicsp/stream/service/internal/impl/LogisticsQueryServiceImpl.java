package com.aicsp.stream.service.internal.impl;

import com.aicsp.stream.service.internal.LogisticsQueryService;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class LogisticsQueryServiceImpl implements LogisticsQueryService {

    @Override
    public Mono<Map<String, Object>> query(Map<String, String> params) {
        return Mono.just(Map.of("domain", "logistics", "params", params));
    }
}
