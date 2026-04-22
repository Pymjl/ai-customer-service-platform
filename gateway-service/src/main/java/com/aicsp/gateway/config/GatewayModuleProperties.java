package com.aicsp.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.route")
public class GatewayModuleProperties {

    private String streamServiceUri = "http://localhost:8082";

    public String getStreamServiceUri() {
        return streamServiceUri;
    }

    public void setStreamServiceUri(String streamServiceUri) {
        this.streamServiceUri = streamServiceUri;
    }
}
