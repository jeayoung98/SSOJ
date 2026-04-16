package com.example.ssoj.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class PythonExecutor implements LanguageExecutor {

    private static final Logger log = LoggerFactory.getLogger(PythonExecutor.class);
    private static final String SOURCE_FILE_NAME = "main.py";

    private final String dockerImage;

    public PythonExecutor(@Value("${worker.executor.python.image:python:3.11}") String dockerImage) {
        this.dockerImage = dockerImage;
    }

    @Override
    public boolean supports(String language) {
        return "python".equalsIgnoreCase(language);
    }

    @Override
    public JudgeExecutionResult execute(JudgeContext context) {
        Path tempDirectory = null;
        Process process = null;
        try {
            tempDirectory = Files.createTempDirectory("judge-python-");
            Files.writeString(tempDirectory.resolve(SOURCE_FILE_NAME), context.sourceCode(), StandardCharsets.UTF_8);

            List<String> command = List.of(
                    "docker",
                    "run",
                    "--rm",
                    "-i",
                    "--network",
                    "none",
                    "-m",
                    context.memoryLimitMb() + "m",
                    "--cpus",
                    "1",
                    "-v",
                    tempDirectory.toAbsolutePath() + ":/workspace",
                    "-w",
                    "/workspace",
                    dockerImage,
                    "sh",
                    "-lc",
                    "python3 main.py"
            );

            Instant startedAt = Instant.now();
            process = new ProcessBuilder(command).start();

            try (OutputStream outputStream = process.getOutputStream()) {
                outputStream.write(context.input().getBytes(StandardCharsets.UTF_8));
            }

            boolean finished = process.waitFor(context.timeLimitMs(), TimeUnit.MILLISECONDS);
            long executionTimeMs = Duration.between(startedAt, Instant.now()).toMillis();

            if (!finished) {
                process.destroyForcibly();
                return JudgeExecutionResult.timeout((int) executionTimeMs);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean success = process.exitValue() == 0;

            return new JudgeExecutionResult(success, stdout, stderr, process.exitValue(), (int) executionTimeMs, null, false, false);
        } catch (IOException exception) {
            log.warn("Python Docker execution failed for submission {}", context.submissionId(), exception);
            return JudgeExecutionResult.systemError(exception.getMessage());
        } catch (Exception exception) {
            log.warn("Python execution failed for submission {}", context.submissionId(), exception);
            return JudgeExecutionResult.systemError(exception.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
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
