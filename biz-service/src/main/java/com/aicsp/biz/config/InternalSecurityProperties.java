package com.aicsp.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "internal")
public class InternalSecurityProperties {

    private String bizToken;
}
