package com.example.ssoj.judge;

import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.executor.DockerProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "run.docker.executor.tests", matches = "true")
class DockerProcessExecutorTest {

    @Test
    void executeRun_completesLongRunningCommandWithinTimeLimit() throws Exception {
        // 시간이 꽤 걸리는 명령이라도 제한 시간 안에 끝나면 정상 성공이어야 한다.
        DockerProcessExecutor dockerProcessExecutor = new DockerProcessExecutor();
        Path workspaceDirectory = Files.createTempDirectory("docker-process-test-");

        try {
            JudgeContext context = new JudgeContext(
                    100L,
                    200L,
                    "cpp",
                    "",
                    "",
                    3000,
                    128
            );

            JudgeExecutionResult result = dockerProcessExecutor.executeRun(
                    context,
                    workspaceDirectory,
                    "gcc:13",
                    128,
                    "sleep 1 && printf done"
            );

            assertThat(result.success()).isTrue();
            assertThat(result.timedOut()).isFalse();
            assertThat(result.systemError()).isFalse();
            assertThat(result.exitCode()).isEqualTo(0);
            assertThat(result.stdout()).isEqualTo("done");
            assertThat(result.executionTimeMs()).isNotNull();
            assertThat(result.executionTimeMs()).isGreaterThanOrEqualTo(900);
            assertThat(result.executionTimeMs()).isLessThan(3000);
            assertThat(result.memoryUsageKb()).isNotNull();
            assertThat(result.memoryUsageKb()).isGreaterThan(0);
        } finally {
            if (Files.exists(workspaceDirectory)) {
                try (var paths = Files.walk(workspaceDirectory)) {
                    paths.sorted((left, right) -> right.compareTo(left))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (Exception ignored) {
                                }
                            });
                }
            }
        }
    }

    @Test
    void executeRun_acceptsSimpleProgramWithinThreeSecondsAndMeasuresMetrics() throws Exception {
        DockerProcessExecutor dockerProcessExecutor = new DockerProcessExecutor();
        Path workspaceDirectory = Files.createTempDirectory("docker-process-ac-test-");

        try {
            JudgeContext context = new JudgeContext(
                    102L,
                    202L,
                    "python",
                    "",
                    "1 2\n",
                    3000,
                    128
            );

            JudgeExecutionResult result = dockerProcessExecutor.executeRun(
                    context,
                    workspaceDirectory,
                    "python:3.11",
                    128,
                    "python3 -c 'a,b=map(int,input().split()); print(a+b)'"
            );

            assertThat(result.success()).isTrue();
            assertThat(result.timedOut()).isFalse();
            assertThat(result.stdout()).isEqualTo("3\n");
            assertThat(result.executionTimeMs()).isNotNull();
            assertThat(result.memoryUsageKb()).isNotNull();
        } finally {
            if (Files.exists(workspaceDirectory)) {
                try (var paths = Files.walk(workspaceDirectory)) {
                    paths.sorted((left, right) -> right.compareTo(left))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (Exception ignored) {
                                }
                            });
                }
            }
        }
    }

    @Test
    void executeRun_timesOutWhenTimeoutCommandUsesFractionalSeconds() throws Exception {
        DockerProcessExecutor dockerProcessExecutor = new DockerProcessExecutor();
        Path workspaceDirectory = Files.createTempDirectory("docker-process-timeout-test-");

        try {
            JudgeContext context = new JudgeContext(
                    101L,
                    201L,
                    "python",
                    "",
                    "",
                    1000,
                    128
            );

            JudgeExecutionResult result = dockerProcessExecutor.executeRun(
                    context,
                    workspaceDirectory,
                    "python:3.11",
                    128,
                    "python3 -c \"import time; time.sleep(2)\""
            );

            assertThat(result.success()).isFalse();
            assertThat(result.timedOut()).isTrue();
            assertThat(result.exitCode()).isEqualTo(124);
            assertThat(result.executionTimeMs()).isNotNull();
            assertThat(result.memoryUsageKb()).isNotNull();
        } finally {
            if (Files.exists(workspaceDirectory)) {
                try (var paths = Files.walk(workspaceDirectory)) {
                    paths.sorted((left, right) -> right.compareTo(left))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (Exception ignored) {
                                }
                            });
                }
            }
        }
    }
}
