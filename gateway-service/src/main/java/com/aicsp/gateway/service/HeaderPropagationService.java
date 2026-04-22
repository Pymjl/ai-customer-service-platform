package com.aicsp.gateway.service;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HeaderPropagationService {

    public String resolveTraceId(String candidateTraceId) {
        return StringUtils.hasText(candidateTraceId) ? candidateTraceId : UUID.randomUUID().toString();
    }

    public void sanitizeIdentityHeaders(HttpHeaders headers) {
        headers.remove("X-User-Id");
        headers.remove("X-Tenant-Id");
        headers.remove("X-User-Roles");
    }
}
