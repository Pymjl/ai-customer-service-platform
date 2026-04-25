package com.aicsp.user.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConfigurationProperties(prefix = "aicsp.jwt")
public class JwtProperties implements InitializingBean {
    private String secret;
    private long ttlSeconds = 7200;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(secret) || secret.getBytes().length < 32) {
            throw new IllegalStateException("aicsp.jwt.secret must be configured and at least 32 bytes");
        }
    }
}
