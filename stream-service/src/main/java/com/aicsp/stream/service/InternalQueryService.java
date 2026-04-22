package com.aicsp.stream.service;

import java.util.Map;
import reactor.core.publisher.Mono;

public interface InternalQueryService {

    Mono<Map<String, Object>> query(String functionName, Map<String, String> params);
}
