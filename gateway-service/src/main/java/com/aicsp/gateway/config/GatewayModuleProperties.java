package com.aicsp.gateway.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "gateway.security")
public class GatewayModuleProperties implements InitializingBean {
    private String introspectUri;
    private String authorizeUri;
    private String internalToken;
    private List<String> whitelistPrefixes = new ArrayList<>(List.of("/api/auth/", "/oauth2/", "/actuator/health"));
    private int rateLimitPerMinute = 120;

    public String getIntrospectUri() { return introspectUri; }
    public void setIntrospectUri(String introspectUri) { this.introspectUri = introspectUri; }
    public String getAuthorizeUri() { return authorizeUri; }
    public void setAuthorizeUri(String authorizeUri) { this.authorizeUri = authorizeUri; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
    public List<String> getWhitelistPrefixes() { return whitelistPrefixes; }
    public void setWhitelistPrefixes(List<String> whitelistPrefixes) { this.whitelistPrefixes = whitelistPrefixes; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(introspectUri)) {
            throw new IllegalStateException("gateway.security.introspect-uri must be configured");
        }
        if (!StringUtils.hasText(authorizeUri)) {
            throw new IllegalStateException("gateway.security.authorize-uri must be configured");
        }
        if (!StringUtils.hasText(internalToken) || internalToken.getBytes().length < 32) {
            throw new IllegalStateException("gateway.security.internal-token must be configured and at least 32 bytes");
        }
    }
}
