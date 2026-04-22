package com.aicsp.stream.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stream")
public class StreamModuleProperties {

    private String internalToken = "change-me";
    private String pythonEngineBaseUrl = "http://localhost:9000";

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public String getPythonEngineBaseUrl() {
        return pythonEngineBaseUrl;
    }

    public void setPythonEngineBaseUrl(String pythonEngineBaseUrl) {
        this.pythonEngineBaseUrl = pythonEngineBaseUrl;
    }
}
