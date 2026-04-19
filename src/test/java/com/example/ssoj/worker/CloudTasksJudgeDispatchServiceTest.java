package com.example.ssoj.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudTasksJudgeDispatchServiceTest {

    @Mock
    private CloudTasksGateway cloudTasksGateway;

    @Test
    void dispatch_createsHttpTaskWithSubmissionIdPayload() {
        CloudTasksDispatchProperties properties = new CloudTasksDispatchProperties(
                "demo-project",
                "asia-northeast3",
                "judge-queue",
                "https://orchestrator.example/internal/judge-executions",
                "worker@example.iam.gserviceaccount.com",
                "https://orchestrator.example"
        );
        CloudTasksJudgeDispatchService service = new CloudTasksJudgeDispatchService(cloudTasksGateway, properties);

        when(cloudTasksGateway.createHttpTask(
                "demo-project",
                "asia-northeast3",
                "judge-queue",
                "https://orchestrator.example/internal/judge-executions",
                "worker@example.iam.gserviceaccount.com",
                "https://orchestrator.example",
                "{\"submissionId\":123}",
                "req-1"
        )).thenReturn("projects/demo-project/locations/asia-northeast3/queues/judge-queue/tasks/task-1");

        service.dispatch(new JudgeDispatchCommand(123L, "req-1"));

        verify(cloudTasksGateway).createHttpTask(
                "demo-project",
                "asia-northeast3",
                "judge-queue",
                "https://orchestrator.example/internal/judge-executions",
                "worker@example.iam.gserviceaccount.com",
                "https://orchestrator.example",
                "{\"submissionId\":123}",
                "req-1"
        );
    }

    @Test
    void dispatch_failsClearlyWhenRequiredConfigurationIsMissing() {
        CloudTasksDispatchProperties properties = new CloudTasksDispatchProperties(
                "",
                "asia-northeast3",
                "judge-queue",
                "https://orchestrator.example/internal/judge-executions",
                null,
                null
        );
        CloudTasksJudgeDispatchService service = new CloudTasksJudgeDispatchService(cloudTasksGateway, properties);

        assertThatThrownBy(() -> service.dispatch(JudgeDispatchCommand.fromSubmissionId(123L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Missing required property: judge.dispatch.cloud-tasks.project-id");
    }
}
