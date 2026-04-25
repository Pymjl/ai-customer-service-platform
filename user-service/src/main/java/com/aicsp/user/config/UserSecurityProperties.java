package com.aicsp.user.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConfigurationProperties(prefix = "aicsp.security")
public class UserSecurityProperties implements InitializingBean {
    private String internalToken;

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(internalToken) || internalToken.getBytes().length < 32) {
            throw new IllegalStateException("aicsp.security.internal-token must be configured and at least 32 bytes");
        }
    }
}
