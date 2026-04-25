package com.aicsp.biz.config;

import com.aicsp.common.util.DistributedIdUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DistributedIdConfig {
    private final Long workerId;

    public DistributedIdConfig(@Value("${aicsp.worker-id:2}") Long workerId) {
        this.workerId = workerId;
    }

    @PostConstruct
    public void init() {
        DistributedIdUtils.setWorkerId(workerId);
    }
}
