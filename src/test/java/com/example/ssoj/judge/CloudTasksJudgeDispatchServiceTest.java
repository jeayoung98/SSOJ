package com.example.ssoj.judge;

import com.example.ssoj.judge.infrastructure.cloudtasks.CloudTasksGateway;
import com.example.ssoj.judge.infrastructure.cloudtasks.CloudTasksJudgeDispatchService;
import com.example.ssoj.judge.domain.model.JudgeDispatchCommand;
import com.example.ssoj.judge.infrastructure.config.CloudTasksDispatchProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudTasksJudgeDispatchServiceTest {

    private static final Long SUBMISSION_ID = 123L;

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
                "{\"submissionId\":\"123\"}",
                "req-1"
        )).thenReturn("projects/demo-project/locations/asia-northeast3/queues/judge-queue/tasks/task-1");

        service.dispatch(new JudgeDispatchCommand(SUBMISSION_ID, "req-1"));

        verify(cloudTasksGateway).createHttpTask(
                "demo-project",
                "asia-northeast3",
                "judge-queue",
                "https://orchestrator.example/internal/judge-executions",
                "worker@example.iam.gserviceaccount.com",
                "https://orchestrator.example",
                "{\"submissionId\":\"123\"}",
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

        assertThatThrownBy(() -> service.dispatch(JudgeDispatchCommand.fromSubmissionId(SUBMISSION_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Missing required property: judge.dispatch.cloud-tasks.project-id");
    }
}
