package com.example.ssoj.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class DockerProcessExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerProcessExecutor.class);
    private static final String CPU_LIMIT = "1";

    public JudgeExecutionResult execute(JudgeContext context, Path workspaceDirectory, String dockerImage, String containerCommand)
            throws IOException, InterruptedException {
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
                CPU_LIMIT,
                "-v",
                workspaceDirectory.toAbsolutePath() + ":/workspace",
                "-w",
                "/workspace",
                dockerImage,
                "sh",
                "-lc",
                containerCommand
        );

        Process process = null;
        try {
            log.info(
                    "Starting Docker execution for submission {} with image={} command={}",
                    context.submissionId(),
                    dockerImage,
                    containerCommand
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
                log.info(
                        "Docker execution timed out for submission {} after {} ms with image={}",
                        context.submissionId(),
                        executionTimeMs,
                        dockerImage
                );
                return JudgeExecutionResult.timeout((int) executionTimeMs);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();

            log.info(
                    "Docker execution finished for submission {} with image={} exitCode={} memoryLimit={}m cpuLimit={}",
                    context.submissionId(),
                    dockerImage,
                    exitCode,
                    context.memoryLimitMb(),
                    CPU_LIMIT
            );
            return new JudgeExecutionResult(exitCode == 0, stdout, stderr, exitCode, (int) executionTimeMs, null, false, false);
        } catch (IOException exception) {
            log.warn(
                    "Failed to start Docker for submission {} with image={} command={}",
                    context.submissionId(),
                    dockerImage,
                    containerCommand,
                    exception
            );
            throw exception;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
