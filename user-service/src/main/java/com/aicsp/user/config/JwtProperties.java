package com.aicsp.user.config;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConfigurationProperties(prefix = "aicsp.jwt")
public class JwtProperties implements InitializingBean {
    private String secret;
    private long ttlSeconds = 900;
    private long refreshTtlSeconds = 259200;
    private int refreshTokenBytes = 32;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    public long getRefreshTtlSeconds() { return refreshTtlSeconds; }
    public void setRefreshTtlSeconds(long refreshTtlSeconds) { this.refreshTtlSeconds = refreshTtlSeconds; }
    public int getRefreshTokenBytes() { return refreshTokenBytes; }
    public void setRefreshTokenBytes(int refreshTokenBytes) { this.refreshTokenBytes = refreshTokenBytes; }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("aicsp.jwt.secret must be configured and at least 32 bytes");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalStateException("aicsp.jwt.ttl-seconds must be positive");
        }
        if (refreshTtlSeconds <= ttlSeconds) {
            throw new IllegalStateException("aicsp.jwt.refresh-ttl-seconds must be greater than ttl-seconds");
        }
        if (refreshTokenBytes < 32) {
            throw new IllegalStateException("aicsp.jwt.refresh-token-bytes must be at least 32");
        }
    }
}
