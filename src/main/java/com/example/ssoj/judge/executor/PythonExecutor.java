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
public class PythonExecutor implements LanguageExecutor {

    private static final Logger log = LoggerFactory.getLogger(PythonExecutor.class);
    private static final String SOURCE_FILE_NAME = "main.py";

    private final String dockerImage;
    private final DockerProcessExecutor dockerProcessExecutor;
    private final WorkspaceDirectoryFactory workspaceDirectoryFactory;

    public PythonExecutor(
            @Value("${worker.executor.python.image:python:3.11}") String dockerImage,
            DockerProcessExecutor dockerProcessExecutor,
            WorkspaceDirectoryFactory workspaceDirectoryFactory
    ) {
        this.dockerImage = dockerImage;
        this.dockerProcessExecutor = dockerProcessExecutor;
        this.workspaceDirectoryFactory = workspaceDirectoryFactory;
    }

    @Override
    public boolean supports(String language) {
        return "python".equalsIgnoreCase(language);
    }

    @Override
    public JudgeExecutionResult execute(JudgeContext context) {
        Path tempDirectory = null;
        try {
            tempDirectory = workspaceDirectoryFactory.create("judge-python-");
            Files.writeString(tempDirectory.resolve(SOURCE_FILE_NAME), context.sourceCode(), StandardCharsets.UTF_8);

            return dockerProcessExecutor.execute(context, tempDirectory, dockerImage, "python3 main.py");
        } catch (IOException exception) {
            log.warn("Python Docker execution failed for submission {}", context.submissionId(), exception);
            return JudgeExecutionResult.systemError(exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Python execution interrupted for submission {}", context.submissionId(), exception);
            return JudgeExecutionResult.systemError(exception.getMessage());
        } catch (Exception exception) {
            log.warn("Python execution failed for submission {}", context.submissionId(), exception);
            return JudgeExecutionResult.systemError(exception.getMessage());
        } finally {
            deleteDirectory(tempDirectory);
        }
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
