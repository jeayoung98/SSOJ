package com.example.ssoj.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "judge.dispatch.mode", havingValue = "cloud-tasks")
@ConditionalOnProperty(name = "worker.role", havingValue = "orchestrator", matchIfMissing = true)
@EnableConfigurationProperties(CloudTasksDispatchProperties.class)
public class CloudTasksJudgeDispatchService implements JudgeDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(CloudTasksJudgeDispatchService.class);

    private final CloudTasksGateway cloudTasksGateway;
    private final CloudTasksDispatchProperties properties;

    public CloudTasksJudgeDispatchService(
            CloudTasksGateway cloudTasksGateway,
            CloudTasksDispatchProperties properties
    ) {
        this.cloudTasksGateway = cloudTasksGateway;
        this.properties = properties;
    }

    @Override
    public void dispatch(JudgeDispatchCommand command) {
        validateConfiguration();

        String payloadJson = "{\"submissionId\":%d}".formatted(command.submissionId());
        String taskName = cloudTasksGateway.createHttpTask(
                properties.projectId(),
                properties.location(),
                properties.queueName(),
                properties.targetUrl(),
                properties.serviceAccountEmail(),
                properties.oidcAudience(),
                payloadJson,
                command.requestId()
        );

        log.info(
                "Created Cloud Tasks dispatch task {} for submissionId={} requestId={}",
                taskName,
                command.submissionId(),
                command.requestId()
        );
    }

    private void validateConfiguration() {
        require(properties.projectId(), "judge.dispatch.cloud-tasks.project-id");
        require(properties.location(), "judge.dispatch.cloud-tasks.location");
        require(properties.queueName(), "judge.dispatch.cloud-tasks.queue-name");
        require(properties.targetUrl(), "judge.dispatch.cloud-tasks.target-url");
    }

    private void require(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + propertyName);
        }
    }
}
