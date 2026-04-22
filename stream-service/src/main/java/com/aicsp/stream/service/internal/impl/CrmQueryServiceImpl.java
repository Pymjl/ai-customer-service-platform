package com.aicsp.stream.service.internal.impl;

import com.aicsp.stream.service.internal.CrmQueryService;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CrmQueryServiceImpl implements CrmQueryService {

    @Override
    public Mono<Map<String, Object>> query(Map<String, String> params) {
        return Mono.just(Map.of("domain", "crm", "params", params));
    }
}
