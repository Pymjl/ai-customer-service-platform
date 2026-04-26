package com.aicsp.biz.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.filter.logging.Slf4jLogFilter;
import com.alibaba.druid.pool.DruidDataSource;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DebugSqlLoggingConfig {

    @Bean
    public ApplicationRunner debugSqlLoggingRunner(DruidDataSource dataSource, Environment environment) {
        return args -> {
            if (!isDebugMode(environment)) {
                return;
            }
            enableDruidExecutableSqlLog(dataSource);
            setLoggerLevel("com.alibaba.druid.filter.logging.Slf4jLogFilter", Level.DEBUG);
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

    private void enableDruidExecutableSqlLog(DruidDataSource dataSource) {
        Slf4jLogFilter filter = findSlf4jLogFilter(dataSource.getProxyFilters());
        if (filter == null) {
            filter = new Slf4jLogFilter();
            dataSource.getProxyFilters().add(filter);
        }
        filter.setStatementLogEnabled(true);
        filter.setStatementExecutableSqlLogEnable(true);
        filter.setStatementPrepareAfterLogEnabled(true);
        filter.setStatementParameterSetLogEnabled(true);
        filter.setStatementExecuteAfterLogEnabled(true);
        filter.setResultSetLogEnabled(false);
    }

    private Slf4jLogFilter findSlf4jLogFilter(List<Filter> filters) {
        return filters.stream()
                .filter(Slf4jLogFilter.class::isInstance)
                .map(Slf4jLogFilter.class::cast)
                .findFirst()
                .orElse(null);
    }

    private void setLoggerLevel(String name, Level level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(name).setLevel(level);
    }
}
