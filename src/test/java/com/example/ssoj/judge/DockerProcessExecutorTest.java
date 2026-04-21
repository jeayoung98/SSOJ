package com.example.ssoj.judge;

import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.executor.DockerProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "run.docker.executor.tests", matches = "true")
class DockerProcessExecutorTest {

    @Test
    void execute_completesLongRunningCommandWithinTimeLimit() throws Exception {
        // 시간이 꽤 걸리는 명령이라도 제한 시간 안에 끝나면 정상 성공이어야 한다.
        DockerProcessExecutor dockerProcessExecutor = new DockerProcessExecutor();
        Path workspaceDirectory = Files.createTempDirectory("docker-process-test-");

        try {
            JudgeContext context = new JudgeContext(
                    UUID.fromString("00000000-0000-0000-0000-000000000100"),
                    "200",
                    "cpp",
                    "",
                    "",
                    3000,
                    128
            );

            JudgeExecutionResult result = dockerProcessExecutor.execute(
                    context,
                    workspaceDirectory,
                    "gcc:13",
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
