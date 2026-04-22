package com.aicsp.stream.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "python.engine")
public class PythonEngineProperties {

    private String baseUrl;
    private Integer maxConnections = 200;
    private Integer acquireTimeout = 3000;
    private Integer connectTimeout = 5000;
    private Integer responseTimeout = 310000;
}
