package com.aicsp.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "python.engine")
public class PythonEngineProperties {

    private String baseUrl = "http://localhost:8000";
    private String internalToken;
}
