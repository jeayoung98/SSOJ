package com.example.ssoj.judge;

import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.executor.DockerProcessExecutor;
import com.example.ssoj.judge.executor.JavaExecutor;
import com.example.ssoj.judge.executor.WorkspaceDirectoryFactory;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class JavaExecutorTest {

    private final WorkspaceDirectoryFactory workspaceDirectoryFactory = new WorkspaceDirectoryFactory(
            System.getProperty("java.io.tmpdir") + "/ssoj-test-workspaces"
    );

    @Test
    void executeSubmission_compilesOnceAndAppliesJavaMemoryPolicy() {
        RecordingDockerProcessExecutor dockerProcessExecutor = new RecordingDockerProcessExecutor(
                List.of(
                        new JudgeExecutionResult(true, "3\n", "", 0, 15, 128, false, false, false, false),
                        new JudgeExecutionResult(true, "5\n", "", 0, 18, 256, false, false, false, false)
                )
        );
        JavaExecutor javaExecutor = new JavaExecutor(
                "eclipse-temurin:17-jdk",
                15000L,
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeRunResult result = javaExecutor.executeSubmission(context());

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.AC);
        assertThat(result.executionTimeMs()).isEqualTo(18);
        assertThat(result.memoryKb()).isEqualTo(256);
        assertThat(dockerProcessExecutor.compileCallCount).isEqualTo(1);
        assertThat(dockerProcessExecutor.runCallCount).isEqualTo(2);
        assertThat(dockerProcessExecutor.compileCommand).isEqualTo("javac Main.java");
        assertThat(dockerProcessExecutor.runCommand).isEqualTo("java -Xmx128m Main");
        assertThat(dockerProcessExecutor.dockerMemoryMb).isEqualTo(256);
        assertThat(Files.exists(dockerProcessExecutor.workspaceDirectory)).isFalse();
    }

    private JudgeRunContext context() {
        return new JudgeRunContext(
                1L,
                10L,
                "java",
                "public class Main { public static void main(String[] args) { System.out.println(1); } }",
                List.of(
                        new HiddenTestCaseSnapshot(1L, 1, "1 2\n", "3\n"),
                        new HiddenTestCaseSnapshot(2L, 2, "2 3\n", "5\n")
                ),
                3000,
                128
        );
    }

    static class RecordingDockerProcessExecutor extends DockerProcessExecutor {
        private final Queue<JudgeExecutionResult> runResults;
        private Path workspaceDirectory;
        private int dockerMemoryMb;
        private String compileCommand;
        private String runCommand;
        private int compileCallCount;
        private int runCallCount;

        RecordingDockerProcessExecutor(List<JudgeExecutionResult> runResults) {
            this.runResults = new ArrayDeque<>(runResults);
        }

        @Override
        public JudgeExecutionResult executeCompile(
                JudgeContext context,
                Path workspaceDirectory,
                String dockerImage,
                int dockerMemoryMb,
                String compileCommand,
                long compileTimeoutMs
        ) {
            this.workspaceDirectory = workspaceDirectory;
            this.dockerMemoryMb = dockerMemoryMb;
            this.compileCommand = compileCommand;
            this.compileCallCount++;
            return new JudgeExecutionResult(true, "", "", 0, null, null, false, false, false, false);
        }

        @Override
        public JudgeExecutionResult executeRun(
                JudgeContext context,
                Path workspaceDirectory,
                String dockerImage,
                int dockerMemoryMb,
                String runCommand
        ) {
            this.workspaceDirectory = workspaceDirectory;
            this.dockerMemoryMb = dockerMemoryMb;
            this.runCommand = runCommand;
            this.runCallCount++;
            return runResults.remove();
        }
    }
}
