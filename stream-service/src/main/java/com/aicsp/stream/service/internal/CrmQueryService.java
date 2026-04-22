package com.aicsp.stream.service.internal;

import java.util.Map;
import reactor.core.publisher.Mono;

public interface CrmQueryService {

    Mono<Map<String, Object>> query(Map<String, String> params);
}
