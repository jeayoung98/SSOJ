package com.example.ssoj.worker;

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
    private static final String CONTAINER_COMMAND = "g++ main.cpp -O2 -std=c++17 -o main && ./main";

    private final String dockerImage;
    private final DockerProcessExecutor dockerProcessExecutor;

    public CppExecutor(
            @Value("${worker.executor.cpp.image:gcc:13}") String dockerImage,
            DockerProcessExecutor dockerProcessExecutor
    ) {
        this.dockerImage = dockerImage;
        this.dockerProcessExecutor = dockerProcessExecutor;
    }

    @Override
    public boolean supports(String language) {
        return "cpp".equalsIgnoreCase(language);
    }

    @Override
    public JudgeExecutionResult execute(JudgeContext context) {
        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("judge-cpp-");
            Files.writeString(tempDirectory.resolve(SOURCE_FILE_NAME), context.sourceCode(), StandardCharsets.UTF_8);
            return dockerProcessExecutor.execute(context, tempDirectory, dockerImage, CONTAINER_COMMAND);
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
        } catch (IOException exception) {
            log.warn("Failed to clean temp directory {}", directory, exception);
        }
    }
}
