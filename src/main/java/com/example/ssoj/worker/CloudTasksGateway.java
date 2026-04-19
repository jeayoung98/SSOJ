package com.example.ssoj.worker;

public interface CloudTasksGateway {

    String createHttpTask(
            String projectId,
            String location,
            String queueName,
            String targetUrl,
            String serviceAccountEmail,
            String oidcAudience,
            String payloadJson,
            String requestId
    );
}
