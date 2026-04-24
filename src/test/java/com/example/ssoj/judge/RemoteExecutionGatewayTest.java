package com.example.ssoj.judge;

import com.example.ssoj.judge.application.port.RemoteExecutionClient;
import com.example.ssoj.judge.infrastructure.remote.RemoteExecutionGateway;
import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteExecutionGatewayTest {

    @Mock
    private RemoteExecutionClient remoteExecutionClient;

    @Test
    void execute_mapsRunnerResponseToJudgeExecutionResult() {
        RemoteExecutionGateway gateway = new RemoteExecutionGateway(remoteExecutionClient, "java,python,cpp");
        JudgeContext context = new JudgeContext(1L, 2L, "java", "class Main {}", "1 2", 1000, 128);

        when(remoteExecutionClient.execute(RunnerExecutionRequest.from(context)))
                .thenReturn(new RunnerExecutionResponse(
                        false,
                        "",
                        "compile error",
                        1,
                        42,
                        256,
                        false,
                        false,
                        true,
                        false
                ));

        JudgeExecutionResult result = gateway.execute(context);

        assertThat(result.success()).isFalse();
        assertThat(result.stderr()).isEqualTo("compile error");
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.executionTimeMs()).isEqualTo(42);
        assertThat(result.memoryUsageKb()).isEqualTo(256);
        assertThat(result.systemError()).isFalse();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.compilationError()).isTrue();
        assertThat(result.memoryLimitExceeded()).isFalse();
    }

    @Test
    void runnerExecutionResponse_from_preservesExecutionMetrics() {
        RunnerExecutionResponse response = RunnerExecutionResponse.from(new JudgeExecutionResult(
                false,
                "",
                "oom",
                137,
                1234,
                54321,
                false,
                false,
                false,
                true
        ));

        assertThat(response.executionTimeMs()).isEqualTo(1234);
        assertThat(response.memoryUsageKb()).isEqualTo(54321);
        assertThat(response.memoryLimitExceeded()).isTrue();
    }

    @Test
    void execute_mapsMemoryLimitExceededMetricFromRunnerResponse() {
        RemoteExecutionGateway gateway = new RemoteExecutionGateway(remoteExecutionClient, "java,python,cpp");
        JudgeContext context = new JudgeContext(1L, 2L, "python", "print(1)", "", 1000, 128);

        when(remoteExecutionClient.execute(RunnerExecutionRequest.from(context)))
                .thenReturn(new RunnerExecutionResponse(
                        false,
                        "",
                        "Killed",
                        137,
                        777,
                        222222,
                        false,
                        false,
                        false,
                        true
                ));

        JudgeExecutionResult result = gateway.execute(context);

        assertThat(result.executionTimeMs()).isEqualTo(777);
        assertThat(result.memoryUsageKb()).isEqualTo(222222);
        assertThat(result.memoryLimitExceeded()).isTrue();
    }

    @Test
    void execute_returnsSystemErrorWhenRemoteClientFails() {
        RemoteExecutionGateway gateway = new RemoteExecutionGateway(remoteExecutionClient, "java,python,cpp");
        JudgeContext context = new JudgeContext(1L, 2L, "python", "print(1)", "", 1000, 128);

        when(remoteExecutionClient.execute(RunnerExecutionRequest.from(context)))
                .thenThrow(new IllegalStateException("runner unavailable"));

        JudgeExecutionResult result = gateway.execute(context);

        assertThat(result.systemError()).isTrue();
        assertThat(result.stderr()).contains("runner unavailable");
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
