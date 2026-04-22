package com.aicsp.stream.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient pythonEngineWebClient(WebClient.Builder builder, PythonEngineProperties properties) {
        return builder.baseUrl(properties.getBaseUrl() == null ? "" : properties.getBaseUrl()).build();
    }
}
