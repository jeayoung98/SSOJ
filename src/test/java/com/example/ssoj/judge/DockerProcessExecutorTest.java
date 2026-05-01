package com.example.ssoj.judge;

import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.executor.DockerProcessExecutor;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "run.docker.executor.tests", matches = "true")
class DockerProcessExecutorTest {

    @Test
    void executeRun_completesLongRunningCommandWithinTimeLimit() throws Exception {
        // ?쒓컙??苑?嫄몃━??紐낅졊?대씪???쒗븳 ?쒓컙 ?덉뿉 ?앸굹硫??뺤긽 ?깃났?댁뼱???쒕떎.
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
                    "ssoj-cpp-runner:13",
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
                    "ssoj-python-runner:3.11",
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
                    "ssoj-python-runner:3.11",
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

    @Test
    void executeBatch_runsPythonTestCasesInSingleContainerAndStopsAtWrongAnswer() throws Exception {
        DockerProcessExecutor dockerProcessExecutor = new DockerProcessExecutor();
        Path workspaceDirectory = Files.createTempDirectory("docker-batch-python-wa-test-");

        try {
            Files.writeString(
                    workspaceDirectory.resolve("main.py"),
                    "print(input())",
                    StandardCharsets.UTF_8
            );

            JudgeRunResult result = dockerProcessExecutor.executeBatch(
                    new JudgeRunContext(
                            103L,
                            203L,
                            "python",
                            "",
                            List.of(
                                    new HiddenTestCaseSnapshot(1L, 1, "ok\n", "ok\n"),
                                    new HiddenTestCaseSnapshot(2L, 2, "actual\n", "expected\n"),
                                    new HiddenTestCaseSnapshot(3L, 3, "skip\n", "skip\n")
                            ),
                            3000,
                            128
                    ),
                    workspaceDirectory,
                    "ssoj-python-runner:3.11",
                    128,
                    null,
                    null,
                    "python3 main.py"
            );

            assertThat(result.finalResult()).isEqualTo(SubmissionResult.WA);
            assertThat(result.failedTestcaseOrder()).isEqualTo(2);
            assertThat(result.executionTimeMs()).isNotNull();
            assertThat(result.memoryKb()).isNotNull();
            assertThat(Files.exists(workspaceDirectory.resolve("output_3.txt"))).isFalse();
        } finally {
            deleteDirectory(workspaceDirectory);
        }
    }

    @Test
    void executeBatch_returnsCompileErrorForJava() throws Exception {
        DockerProcessExecutor dockerProcessExecutor = new DockerProcessExecutor();
        Path workspaceDirectory = Files.createTempDirectory("docker-batch-java-ce-test-");

        try {
            Files.writeString(
                    workspaceDirectory.resolve("Main.java"),
                    "public class Main { syntax error }",
                    StandardCharsets.UTF_8
            );

            JudgeRunResult result = dockerProcessExecutor.executeBatch(
                    new JudgeRunContext(
                            104L,
                            204L,
                            "java",
                            "",
                            List.of(new HiddenTestCaseSnapshot(1L, 1, "", "")),
                            3000,
                            128
                    ),
                    workspaceDirectory,
                    "ssoj-java-runner:17",
                    256,
                    "javac Main.java",
                    15000L,
                    "java -Xmx128m Main"
            );

            assertThat(result.finalResult()).isEqualTo(SubmissionResult.CE);
            assertThat(result.failedTestcaseOrder()).isNull();
            assertThat(result.executionTimeMs()).isNull();
            assertThat(result.memoryKb()).isNull();
        } finally {
            deleteDirectory(workspaceDirectory);
        }
    }

    @Test
    void executeBatch_runsCppTestCasesInSingleContainerAndReturnsAc() throws Exception {
        DockerProcessExecutor dockerProcessExecutor = new DockerProcessExecutor();
        Path workspaceDirectory = Files.createTempDirectory("docker-batch-cpp-ac-test-");

        try {
            Files.writeString(
                    workspaceDirectory.resolve("main.cpp"),
                    """
                            #include <iostream>
                            int main() {
                                int a, b;
                                std::cin >> a >> b;
                                std::cout << a + b << '\\n';
                                return 0;
                            }
                            """,
                    StandardCharsets.UTF_8
            );

            JudgeRunResult result = dockerProcessExecutor.executeBatch(
                    new JudgeRunContext(
                            105L,
                            205L,
                            "cpp",
                            "",
                            List.of(
                                    new HiddenTestCaseSnapshot(1L, 1, "1 2\n", "3\n"),
                                    new HiddenTestCaseSnapshot(2L, 2, "10 20\n", "30\n")
                            ),
                            3000,
                            128
                    ),
                    workspaceDirectory,
                    "ssoj-cpp-runner:13",
                    128,
                    "g++ main.cpp -O2 -std=c++17 -o main",
                    15000L,
                    "./main"
            );

            assertThat(result.finalResult()).isEqualTo(SubmissionResult.AC);
            assertThat(result.failedTestcaseOrder()).isNull();
            assertThat(result.executionTimeMs()).isNotNull();
            assertThat(result.memoryKb()).isNotNull();
        } finally {
            deleteDirectory(workspaceDirectory);
        }
    }

    private void deleteDirectory(Path directory) throws Exception {
        if (!Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
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


