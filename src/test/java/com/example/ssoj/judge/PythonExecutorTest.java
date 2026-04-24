package com.example.ssoj.judge;

import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.executor.DockerProcessExecutor;
import com.example.ssoj.judge.executor.PythonExecutor;
import com.example.ssoj.judge.executor.WorkspaceDirectoryFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PythonExecutorTest {

    private final WorkspaceDirectoryFactory workspaceDirectoryFactory = new WorkspaceDirectoryFactory(
            System.getProperty("java.io.tmpdir") + "/ssoj-test-workspaces"
    );

    @Test
    void execute_createsMainPyUnderWorkspaceBeforeDockerExecution() {
        RecordingDockerProcessExecutor dockerProcessExecutor = new RecordingDockerProcessExecutor(
                new JudgeExecutionResult(true, "3\n", "", 0, 15, 128, false, false, false, false)
        );
        PythonExecutor pythonExecutor = new PythonExecutor("python:3.11", dockerProcessExecutor, workspaceDirectoryFactory);

        JudgeExecutionResult result = pythonExecutor.execute(new JudgeContext(
                1L,
                10L,
                "python",
                "print(1 + 2)",
                "",
                1000,
                64
        ));

        assertThat(result.success()).isTrue();
        assertThat(dockerProcessExecutor.workspaceDirectory).isNotNull();
        assertThat(dockerProcessExecutor.workspaceExistsDuringExecution).isTrue();
        assertThat(dockerProcessExecutor.sourceFileExistsDuringExecution).isTrue();
        assertThat(dockerProcessExecutor.runCommand).isEqualTo("python3 main.py");
        assertThat(dockerProcessExecutor.dockerMemoryMb).isEqualTo(128);
        assertThat(Files.exists(dockerProcessExecutor.workspaceDirectory)).isFalse();
    }

    static class RecordingDockerProcessExecutor extends DockerProcessExecutor {

        private final JudgeExecutionResult result;
        private Path workspaceDirectory;
        private boolean workspaceExistsDuringExecution;
        private boolean sourceFileExistsDuringExecution;
        private String runCommand;
        private int dockerMemoryMb;

        RecordingDockerProcessExecutor(JudgeExecutionResult result) {
            this.result = result;
        }

        @Override
        public JudgeExecutionResult executeRun(
                JudgeContext context,
                Path workspaceDirectory,
                String dockerImage,
                int dockerMemoryMb,
                String runCommand
        ) throws IOException, InterruptedException {
            this.workspaceDirectory = workspaceDirectory;
            this.workspaceExistsDuringExecution = Files.exists(workspaceDirectory);
            this.sourceFileExistsDuringExecution = Files.exists(workspaceDirectory.resolve("main.py"));
            this.runCommand = runCommand;
            this.dockerMemoryMb = dockerMemoryMb;

            return result;
        }
    }
}
