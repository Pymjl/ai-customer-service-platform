package com.aicsp.biz.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DebugSqlLoggingConfig {

    @Bean
    public ApplicationRunner debugSqlLoggingRunner(Environment environment) {
        return args -> {
            if (!isDebugMode(environment)) {
                return;
            }
            setLoggerLevel("com.aicsp.biz.mapper", Level.DEBUG);
            setLoggerLevel("org.mybatis", Level.DEBUG);
        };
    }

    private boolean isDebugMode(Environment environment) {
        boolean springDebug = environment.getProperty("debug", Boolean.class, false);
        boolean debugProfile = Arrays.asList(environment.getActiveProfiles()).contains("debug");
        boolean jvmDebug = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(arg -> arg.contains("jdwp"));
        return springDebug || debugProfile || jvmDebug;
    }

    private void setLoggerLevel(String name, Level level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(name).setLevel(level);
    }
}
