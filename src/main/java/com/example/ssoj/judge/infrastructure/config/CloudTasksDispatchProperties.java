package com.example.ssoj.judge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "judge.dispatch.cloud-tasks")
public record CloudTasksDispatchProperties(
        String projectId,
        String location,
        String queueName,
        String targetUrl,
        String serviceAccountEmail,
        String oidcAudience
) {
}
