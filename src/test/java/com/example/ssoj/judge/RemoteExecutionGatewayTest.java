package com.example.ssoj.judge;

import com.example.ssoj.judge.application.port.RemoteExecutionClient;
import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.infrastructure.remote.RemoteExecutionGateway;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RemoteExecutionGatewayTest {

    @Mock
    private RemoteExecutionClient remoteExecutionClient;

    @Test
    void executeSubmission_includesAllHiddenTestCasesAndMapsResponse() {
        RemoteExecutionGateway gateway = new RemoteExecutionGateway(remoteExecutionClient, "java,python,cpp");
        JudgeRunContext context = new JudgeRunContext(
                1L,
                2L,
                "java",
                "class Main {}",
                List.of(
                        new HiddenTestCaseSnapshot(11L, 1, "1 2", "3"),
                        new HiddenTestCaseSnapshot(12L, 2, "2 3", "5")
                ),
                1000,
                128
        );
        when(remoteExecutionClient.execute(RunnerExecutionRequest.from(context)))
                .thenReturn(new RunnerExecutionResponse(SubmissionResult.WA, 42, 256, 2));

        JudgeRunResult result = gateway.executeSubmission(context);

        ArgumentCaptor<RunnerExecutionRequest> requestCaptor = ArgumentCaptor.forClass(RunnerExecutionRequest.class);
        verify(remoteExecutionClient).execute(requestCaptor.capture());

        assertThat(requestCaptor.getValue().testCases()).hasSize(2);
        assertThat(requestCaptor.getValue().testCases())
                .extracting(testCase -> testCase.input())
                .containsExactly("1 2", "2 3");

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.WA);
        assertThat(result.executionTimeMs()).isEqualTo(42);
        assertThat(result.memoryKb()).isEqualTo(256);
        assertThat(result.failedTestcaseOrder()).isEqualTo(2);
    }

    @Test
    void executeSubmission_returnsSystemErrorWhenRemoteClientFailsAndLogsException(CapturedOutput output) {
        RemoteExecutionGateway gateway = new RemoteExecutionGateway(remoteExecutionClient, "java,python,cpp");
        JudgeRunContext context = new JudgeRunContext(1L, 2L, "python", "print(1)", List.of(), 1000, 128);

        when(remoteExecutionClient.execute(RunnerExecutionRequest.from(context)))
                .thenThrow(new IllegalStateException("runner unavailable"));

        JudgeRunResult result = gateway.executeSubmission(context);

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.SYSTEM_ERROR);
        assertThat(output).contains("Remote execution failed. submissionId=1");
        assertThat(output).contains("java.lang.IllegalStateException: runner unavailable");
    }

    @Test
    void supports_matchesConfiguredLanguagesCaseInsensitively() {
        RemoteExecutionGateway gateway = new RemoteExecutionGateway(remoteExecutionClient, "java,python,cpp");

        assertThat(gateway.supports("JAVA")).isTrue();
        assertThat(gateway.supports("python")).isTrue();
        assertThat(gateway.supports(null)).isFalse();
        assertThat(gateway.supports("unknown")).isFalse();
    }
}
