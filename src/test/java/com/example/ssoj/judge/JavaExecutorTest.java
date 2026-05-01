package com.example.ssoj.judge;

import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
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
                        new JudgeRunResult(SubmissionResult.AC, 35, 512, null)
                )
        );
        JavaExecutor javaExecutor = new JavaExecutor(
                "ssoj-java-runner:17",
                15000L,
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeRunResult result = javaExecutor.executeSubmission(context());

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.AC);
        assertThat(result.executionTimeMs()).isEqualTo(35);
        assertThat(result.memoryKb()).isEqualTo(512);
        assertThat(dockerProcessExecutor.batchCallCount).isEqualTo(1);
        assertThat(dockerProcessExecutor.dockerImage).isEqualTo("ssoj-java-runner:17");
        assertThat(dockerProcessExecutor.compileCommand).isEqualTo("javac Main.java");
        assertThat(dockerProcessExecutor.runCommand).isEqualTo("java -Xmx128m Main");
        assertThat(dockerProcessExecutor.dockerMemoryMb).isEqualTo(256);
        assertThat(Files.exists(dockerProcessExecutor.workspaceDirectory)).isFalse();
    }

    @Test
    void executeSubmission_returnsMaxMetricsUntilFirstWrongAnswer() {
        RecordingDockerProcessExecutor dockerProcessExecutor = new RecordingDockerProcessExecutor(
                List.of(
                        new JudgeRunResult(SubmissionResult.WA, 40, 700, 2)
                )
        );
        JavaExecutor javaExecutor = new JavaExecutor(
                "ssoj-java-runner:17",
                15000L,
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeRunResult result = javaExecutor.executeSubmission(context());

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.WA);
        assertThat(result.executionTimeMs()).isEqualTo(40);
        assertThat(result.memoryKb()).isEqualTo(700);
        assertThat(result.failedTestcaseOrder()).isEqualTo(2);
        assertThat(dockerProcessExecutor.batchCallCount).isEqualTo(1);
    }

    @Test
    void executeSubmission_returnsCompileErrorFromBatchExecution() {
        RecordingDockerProcessExecutor dockerProcessExecutor = new RecordingDockerProcessExecutor(
                List.of(new JudgeRunResult(SubmissionResult.CE, null, null, null))
        );
        JavaExecutor javaExecutor = new JavaExecutor(
                "ssoj-java-runner:17",
                15000L,
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeRunResult result = javaExecutor.executeSubmission(context());

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.CE);
        assertThat(result.executionTimeMs()).isNull();
        assertThat(result.memoryKb()).isNull();
        assertThat(result.failedTestcaseOrder()).isNull();
        assertThat(dockerProcessExecutor.batchCallCount).isEqualTo(1);
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
        private final Queue<JudgeRunResult> runResults;
        private Path workspaceDirectory;
        private String dockerImage;
        private int dockerMemoryMb;
        private String compileCommand;
        private String runCommand;
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
            this.dockerMemoryMb = dockerMemoryMb;
            this.compileCommand = compileCommand;
            this.runCommand = runCommand;
            this.batchCallCount++;
            return runResults.remove();
        }
    }
}


