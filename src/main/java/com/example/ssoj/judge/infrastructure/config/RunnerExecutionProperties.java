package com.example.ssoj.judge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "judge.runner")
public record RunnerExecutionProperties(
        Integer maxConcurrentExecutions
) {

    public RunnerExecutionProperties {
        if (maxConcurrentExecutions == null) {
            maxConcurrentExecutions = 4;
        }
        if (maxConcurrentExecutions < 1) {
            throw new IllegalArgumentException("judge.runner.max-concurrent-executions must be at least 1");
        }
    }
}
