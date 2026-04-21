package com.example.ssoj.judge.executor;

import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class CppExecutor implements LanguageExecutor {

    private static final Logger log = LoggerFactory.getLogger(CppExecutor.class);
    private static final String SOURCE_FILE_NAME = "main.cpp";

    private final String dockerImage;
    private final String compileCommand;
    private final String runCommand;
    private final DockerProcessExecutor dockerProcessExecutor;
    private final WorkspaceDirectoryFactory workspaceDirectoryFactory;

    public CppExecutor(
            @Value("${worker.executor.cpp.image:gcc:13}") String dockerImage,
            @Value("${worker.executor.cpp.compile-command:g++ main.cpp -O2 -std=c++17 -o main}") String compileCommand,
            @Value("${worker.executor.cpp.run-command:./main}") String runCommand,
            DockerProcessExecutor dockerProcessExecutor,
            WorkspaceDirectoryFactory workspaceDirectoryFactory
    ) {
        this.dockerImage = dockerImage;
        this.compileCommand = compileCommand;
        this.runCommand = runCommand;
        this.dockerProcessExecutor = dockerProcessExecutor;
        this.workspaceDirectoryFactory = workspaceDirectoryFactory;
    }

    @Override
    public boolean supports(String language) {
        return "cpp".equalsIgnoreCase(language);
    }

    @Override
    public JudgeExecutionResult execute(JudgeContext context) {
        Path tempDirectory = null;
        try {
            tempDirectory = workspaceDirectoryFactory.create("judge-cpp-");
            Files.writeString(tempDirectory.resolve(SOURCE_FILE_NAME), context.sourceCode(), StandardCharsets.UTF_8);
            log.info(
                    "Executing C++ submission {} with image={}, compileCommand={}, runCommand={}",
                    context.submissionId(),
                    dockerImage,
                    compileCommand,
                    runCommand
            );
            return dockerProcessExecutor.execute(context, tempDirectory, dockerImage, buildContainerCommand());
        } catch (IOException exception) {
            log.warn("C++ Docker execution failed for submission {}", context.submissionId(), exception);
            return JudgeExecutionResult.systemError(exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("C++ execution interrupted for submission {}", context.submissionId(), exception);
            return JudgeExecutionResult.systemError(exception.getMessage());
        } catch (Exception exception) {
            log.warn("C++ execution failed for submission {}", context.submissionId(), exception);
            return JudgeExecutionResult.systemError(exception.getMessage());
        } finally {
            deleteDirectory(tempDirectory);
        }
    }

    private String buildContainerCommand() {
        return compileCommand + " && " + runCommand;
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            log.warn("Failed to delete temp path {}", path, exception);
                        }
                    });
            log.info("Deleted temp directory {}", directory);
        } catch (IOException exception) {
            log.warn("Failed to clean temp directory {}", directory, exception);
        }
    }
}
