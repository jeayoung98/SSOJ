package com.example.ssoj.judge;

import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.executor.CppExecutor;
import com.example.ssoj.judge.executor.DockerProcessExecutor;
import com.example.ssoj.judge.executor.WorkspaceDirectoryFactory;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class CppExecutorTest {

    private final WorkspaceDirectoryFactory workspaceDirectoryFactory = new WorkspaceDirectoryFactory(
            System.getProperty("java.io.tmpdir") + "/ssoj-test-workspaces"
    );

    @Test
    void executeSubmission_deletesTempDirectoryAfterSuccessfulExecution() {
        RecordingDockerProcessExecutor dockerProcessExecutor = new RecordingDockerProcessExecutor(
                List.of(
                        new JudgeRunResult(SubmissionResult.AC, 35, 512, null)
                )
        );
        CppExecutor cppExecutor = new CppExecutor(
                "ssoj-cpp-runner:13",
                15000L,
                "g++ main.cpp -O2 -std=c++17 -o main",
                "./main",
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeRunResult result = cppExecutor.executeSubmission(context());

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.AC);
        assertThat(result.executionTimeMs()).isEqualTo(35);
        assertThat(result.memoryKb()).isEqualTo(512);
        assertThat(dockerProcessExecutor.batchCallCount).isEqualTo(1);
        assertThat(dockerProcessExecutor.dockerImage).isEqualTo("ssoj-cpp-runner:13");
        assertThat(Files.exists(dockerProcessExecutor.workspaceDirectory)).isFalse();
    }

    @Test
    void executeSubmission_returnsMaxMetricsUntilFirstWrongAnswer() {
        RecordingDockerProcessExecutor dockerProcessExecutor = new RecordingDockerProcessExecutor(
                List.of(
                        new JudgeRunResult(SubmissionResult.WA, 40, 700, 2)
                )
        );
        CppExecutor cppExecutor = new CppExecutor(
                "ssoj-cpp-runner:13",
                15000L,
                "g++ main.cpp -O2 -std=c++17 -o main",
                "./main",
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeRunResult result = cppExecutor.executeSubmission(context());

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.WA);
        assertThat(result.executionTimeMs()).isEqualTo(40);
        assertThat(result.memoryKb()).isEqualTo(700);
        assertThat(result.failedTestcaseOrder()).isEqualTo(2);
        assertThat(dockerProcessExecutor.batchCallCount).isEqualTo(1);
    }

    @Test
    void executeSubmission_returnsTleFromBatchExecution() {
        RecordingDockerProcessExecutor dockerProcessExecutor = new RecordingDockerProcessExecutor(
                List.of(new JudgeRunResult(SubmissionResult.TLE, 1000, 200, 2))
        );
        CppExecutor cppExecutor = new CppExecutor(
                "ssoj-cpp-runner:13",
                15000L,
                "g++ main.cpp -O2 -std=c++17 -o main",
                "./main",
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeRunResult result = cppExecutor.executeSubmission(context());

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.TLE);
        assertThat(result.executionTimeMs()).isEqualTo(1000);
        assertThat(result.memoryKb()).isEqualTo(200);
        assertThat(result.failedTestcaseOrder()).isEqualTo(2);
        assertThat(dockerProcessExecutor.batchCallCount).isEqualTo(1);
    }

    @Test
    void executeSubmission_deletesTempDirectoryWhenDockerExecutionThrows() {
        ThrowingDockerProcessExecutor dockerProcessExecutor = new ThrowingDockerProcessExecutor();
        CppExecutor cppExecutor = new CppExecutor(
                "ssoj-cpp-runner:13",
                15000L,
                "g++ main.cpp -O2 -std=c++17 -o main",
                "./main",
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeRunResult result = cppExecutor.executeSubmission(context());

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.SYSTEM_ERROR);
        assertThat(Files.exists(dockerProcessExecutor.workspaceDirectory)).isFalse();
    }

    private JudgeRunContext context() {
        return new JudgeRunContext(
                1L,
                10L,
                "cpp",
                "int main() { return 0; }",
                List.of(
                        new HiddenTestCaseSnapshot(1L, 1, "1 2\n", "3\n"),
                        new HiddenTestCaseSnapshot(2L, 2, "2 3\n", "5\n")
                ),
                1000,
                128
        );
    }

    static class RecordingDockerProcessExecutor extends DockerProcessExecutor {
        private final Queue<JudgeRunResult> runResults;
        private Path workspaceDirectory;
        private String dockerImage;
        private int batchCallCount;

        RecordingDockerProcessExecutor(List<JudgeRunResult> runResults) {
            this.runResults = new ArrayDeque<>(runResults);
        }

        @Override
        public JudgeRunResult executeBatch(
                JudgeRunContext context,
                Path workspaceDirectory,
                String dockerImage,
                int dockerMemoryMb,
                String compileCommand,
                Long compileTimeoutMs,
                String runCommand
        ) {
            this.workspaceDirectory = workspaceDirectory;
            this.dockerImage = dockerImage;
            this.batchCallCount++;
            return runResults.remove();
        }
    }

    static class ThrowingDockerProcessExecutor extends DockerProcessExecutor {
        private Path workspaceDirectory;

        @Override
        public JudgeRunResult executeBatch(
                JudgeRunContext context,
                Path workspaceDirectory,
                String dockerImage,
                int dockerMemoryMb,
                String compileCommand,
                Long compileTimeoutMs,
                String runCommand
        ) throws IOException {
            this.workspaceDirectory = workspaceDirectory;
            throw new IOException("docker start failed");
        }
    }
}


