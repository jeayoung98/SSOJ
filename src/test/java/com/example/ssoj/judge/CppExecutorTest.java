package com.example.ssoj.judge;

import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.executor.CppExecutor;
import com.example.ssoj.judge.executor.DockerProcessExecutor;
import com.example.ssoj.judge.executor.WorkspaceDirectoryFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CppExecutorTest {

    private final WorkspaceDirectoryFactory workspaceDirectoryFactory = new WorkspaceDirectoryFactory(
            System.getProperty("java.io.tmpdir") + "/ssoj-test-workspaces"
    );

    @Test
    void execute_deletesTempDirectoryAfterSuccessfulExecution() throws IOException, InterruptedException {
        // 실제 Docker 호출 대신 workspace 생성과 cleanup 보장 여부만 검증한다.
        RecordingDockerProcessExecutor dockerProcessExecutor = new RecordingDockerProcessExecutor(
                new JudgeExecutionResult(true, "3\n", "", 0, 15, 128, false, false)
        );
        CppExecutor cppExecutor = new CppExecutor(
                "gcc:13",
                "g++ main.cpp -O2 -std=c++17 -o main",
                "./main",
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeExecutionResult result = cppExecutor.execute(context("int main() { return 0; }"));

        assertThat(result.success()).isTrue();
        assertThat(dockerProcessExecutor.workspaceDirectory).isNotNull();
        assertThat(dockerProcessExecutor.workspaceExistsDuringExecution).isTrue();
        assertThat(dockerProcessExecutor.sourceFileExistsDuringExecution).isTrue();
        assertThat(Files.exists(dockerProcessExecutor.workspaceDirectory)).isFalse();
    }

    @Test
    void execute_deletesTempDirectoryWhenDockerExecutionThrows() throws IOException, InterruptedException {
        // 실패 경로에서도 finally cleanup이 동작해야 한다.
        RecordingDockerProcessExecutor dockerProcessExecutor = new RecordingDockerProcessExecutor(
                new IOException("docker start failed")
        );
        CppExecutor cppExecutor = new CppExecutor(
                "gcc:13",
                "g++ main.cpp -O2 -std=c++17 -o main",
                "./main",
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeExecutionResult result = cppExecutor.execute(context("int main() { return 0; }"));

        assertThat(result.systemError()).isTrue();
        assertThat(dockerProcessExecutor.workspaceDirectory).isNotNull();
        assertThat(dockerProcessExecutor.workspaceExistsDuringExecution).isTrue();
        assertThat(dockerProcessExecutor.sourceFileExistsDuringExecution).isTrue();
        assertThat(Files.exists(dockerProcessExecutor.workspaceDirectory)).isFalse();
    }

    private static JudgeContext context(String sourceCode) {
        return new JudgeContext(UUID.fromString("00000000-0000-0000-0000-000000000001"), "10", "cpp", sourceCode, "1 2\n", 1000, 128);
    }

    static class RecordingDockerProcessExecutor extends DockerProcessExecutor {
        // execute 호출 시점의 workspace 상태를 기록해 cleanup 전후를 비교한다.

        private final JudgeExecutionResult result;
        private final Exception exception;
        private Path workspaceDirectory;
        private boolean workspaceExistsDuringExecution;
        private boolean sourceFileExistsDuringExecution;

        RecordingDockerProcessExecutor(JudgeExecutionResult result) {
            this.result = result;
            this.exception = null;
        }

        RecordingDockerProcessExecutor(Exception exception) {
            this.result = null;
            this.exception = exception;
        }

        @Override
        public JudgeExecutionResult execute(
                JudgeContext context,
                Path workspaceDirectory,
                String dockerImage,
                String containerCommand
        ) throws IOException, InterruptedException {
            this.workspaceDirectory = workspaceDirectory;
            this.workspaceExistsDuringExecution = Files.exists(workspaceDirectory);
            this.sourceFileExistsDuringExecution = Files.exists(workspaceDirectory.resolve("main.cpp"));

            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            if (exception instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            return result;
        }
    }
}
