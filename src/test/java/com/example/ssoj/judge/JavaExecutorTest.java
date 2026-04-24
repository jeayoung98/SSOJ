package com.example.ssoj.judge;

import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.executor.DockerProcessExecutor;
import com.example.ssoj.judge.executor.JavaExecutor;
import com.example.ssoj.judge.executor.WorkspaceDirectoryFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JavaExecutorTest {

    private final WorkspaceDirectoryFactory workspaceDirectoryFactory = new WorkspaceDirectoryFactory(
            System.getProperty("java.io.tmpdir") + "/ssoj-test-workspaces"
    );

    @Test
    void execute_appliesProblemMemoryLimitToJavaHeapAndDockerMemory() {
        RecordingDockerProcessExecutor dockerProcessExecutor = new RecordingDockerProcessExecutor(
                new JudgeExecutionResult(true, "3\n", "", 0, 15, 128, false, false, false, false)
        );
        JavaExecutor javaExecutor = new JavaExecutor(
                "eclipse-temurin:17-jdk",
                15000L,
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeExecutionResult result = javaExecutor.execute(new JudgeContext(
                1L,
                10L,
                "java",
                "public class Main { public static void main(String[] args) { System.out.println(3); } }",
                "",
                3000,
                128
        ));

        assertThat(result.success()).isTrue();
        assertThat(dockerProcessExecutor.compileCommand).isEqualTo("javac Main.java");
        assertThat(dockerProcessExecutor.compileTimeoutMs).isEqualTo(15000L);
        assertThat(dockerProcessExecutor.runCommand).isEqualTo("java -Xmx128m Main");
        assertThat(dockerProcessExecutor.dockerMemoryMb).isEqualTo(256);
        assertThat(dockerProcessExecutor.workspaceExistsDuringExecution).isTrue();
        assertThat(dockerProcessExecutor.sourceFileExistsDuringExecution).isTrue();
        assertThat(Files.exists(dockerProcessExecutor.workspaceDirectory)).isFalse();
    }

    static class RecordingDockerProcessExecutor extends DockerProcessExecutor {

        private final JudgeExecutionResult runResult;
        private Path workspaceDirectory;
        private boolean workspaceExistsDuringExecution;
        private boolean sourceFileExistsDuringExecution;
        private int dockerMemoryMb;
        private String compileCommand;
        private long compileTimeoutMs;
        private String runCommand;

        RecordingDockerProcessExecutor(JudgeExecutionResult runResult) {
            this.runResult = runResult;
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
            this.workspaceExistsDuringExecution = Files.exists(workspaceDirectory);
            this.sourceFileExistsDuringExecution = Files.exists(workspaceDirectory.resolve("Main.java"));
            this.dockerMemoryMb = dockerMemoryMb;
            this.compileCommand = compileCommand;
            this.compileTimeoutMs = compileTimeoutMs;
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
            this.runCommand = runCommand;
            this.dockerMemoryMb = dockerMemoryMb;
            return runResult;
        }
    }
}
