package com.aicsp.stream.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "stream.sse")
public class StreamSseProperties {

    private String heartbeatInterval = "15s";
    private String maxDuration = "300s";
}
