package com.example.ssoj.judge;

import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.executor.DockerProcessExecutor;
import com.example.ssoj.judge.executor.PythonExecutor;
import com.example.ssoj.judge.executor.WorkspaceDirectoryFactory;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class PythonExecutorTest {

    private final WorkspaceDirectoryFactory workspaceDirectoryFactory = new WorkspaceDirectoryFactory(
            System.getProperty("java.io.tmpdir") + "/ssoj-test-workspaces"
    );

    @Test
    void executeSubmission_runsAllTestCasesInSameWorkspace() {
        RecordingDockerProcessExecutor dockerProcessExecutor = new RecordingDockerProcessExecutor(
                List.of(
                        new JudgeExecutionResult(true, "3\n", "", 0, 15, 128, false, false, false, false),
                        new JudgeExecutionResult(true, "5\n", "", 0, 18, 256, false, false, false, false)
                )
        );
        PythonExecutor pythonExecutor = new PythonExecutor("python:3.11", dockerProcessExecutor, workspaceDirectoryFactory);

        JudgeRunResult result = pythonExecutor.executeSubmission(context());

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.AC);
        assertThat(result.executionTimeMs()).isEqualTo(18);
        assertThat(result.memoryKb()).isEqualTo(256);
        assertThat(dockerProcessExecutor.runCallCount).isEqualTo(2);
        assertThat(dockerProcessExecutor.runCommand).isEqualTo("python3 main.py");
        assertThat(dockerProcessExecutor.dockerMemoryMb).isEqualTo(128);
        assertThat(Files.exists(dockerProcessExecutor.workspaceDirectory)).isFalse();
    }

    private JudgeRunContext context() {
        return new JudgeRunContext(
                1L,
                10L,
                "python",
                "print(1 + 2)",
                List.of(
                        new HiddenTestCaseSnapshot(1L, 1, "", "3\n"),
                        new HiddenTestCaseSnapshot(2L, 2, "", "5\n")
                ),
                1000,
                64
        );
    }

    static class RecordingDockerProcessExecutor extends DockerProcessExecutor {
        private final Queue<JudgeExecutionResult> runResults;
        private Path workspaceDirectory;
        private String runCommand;
        private int dockerMemoryMb;
        private int runCallCount;

        RecordingDockerProcessExecutor(List<JudgeExecutionResult> runResults) {
            this.runResults = new ArrayDeque<>(runResults);
        }

        @Override
        public JudgeExecutionResult executeRun(JudgeContext context, Path workspaceDirectory, String dockerImage, int dockerMemoryMb, String runCommand) {
            this.workspaceDirectory = workspaceDirectory;
            this.runCommand = runCommand;
            this.dockerMemoryMb = dockerMemoryMb;
            this.runCallCount++;
            return runResults.remove();
        }
    }
}
