package com.example.ssoj.worker;

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
