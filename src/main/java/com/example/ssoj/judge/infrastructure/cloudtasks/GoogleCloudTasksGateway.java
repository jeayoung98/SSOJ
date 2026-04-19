package com.example.ssoj.judge.infrastructure.cloudtasks;

import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.CreateTaskRequest;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.HttpRequest;
import com.google.cloud.tasks.v2.OidcToken;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.Task;
import com.google.protobuf.ByteString;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "judge.dispatch.mode", havingValue = "cloud-tasks")
public class GoogleCloudTasksGateway implements CloudTasksGateway {

    private final CloudTasksClientFactory cloudTasksClientFactory;

    public GoogleCloudTasksGateway(CloudTasksClientFactory cloudTasksClientFactory) {
        this.cloudTasksClientFactory = cloudTasksClientFactory;
    }

    @Override
    public String createHttpTask(
            String projectId,
            String location,
            String queueName,
            String targetUrl,
            String serviceAccountEmail,
            String oidcAudience,
            String payloadJson,
            String requestId
    ) {
        HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                .setHttpMethod(HttpMethod.POST)
                .setUrl(targetUrl)
                .putHeaders("Content-Type", "application/json")
                .setBody(ByteString.copyFromUtf8(payloadJson));

        if (requestId != null && !requestId.isBlank()) {
            httpRequestBuilder.putHeaders("X-Request-Id", requestId);
        }

        if (serviceAccountEmail != null && !serviceAccountEmail.isBlank()) {
            OidcToken.Builder oidcTokenBuilder = OidcToken.newBuilder()
                    .setServiceAccountEmail(serviceAccountEmail);
            if (oidcAudience != null && !oidcAudience.isBlank()) {
                oidcTokenBuilder.setAudience(oidcAudience);
            }
            httpRequestBuilder.setOidcToken(oidcTokenBuilder.build());
        }

        Task task = Task.newBuilder()
                .setHttpRequest(httpRequestBuilder.build())
                .build();

        CreateTaskRequest request = CreateTaskRequest.newBuilder()
                .setParent(QueueName.of(projectId, location, queueName).toString())
                .setTask(task)
                .build();

        try (CloudTasksClient cloudTasksClient = cloudTasksClientFactory.create()) {
            return cloudTasksClient.createTask(request).getName();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to create Cloud Tasks dispatch task for queue %s in %s/%s: %s"
                            .formatted(queueName, projectId, location, exception.getMessage()),
                    exception
            );
        }
    }
}
