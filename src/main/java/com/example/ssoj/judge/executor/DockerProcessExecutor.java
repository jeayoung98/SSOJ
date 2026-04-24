package com.example.ssoj.judge.executor;

import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

@Component
public class DockerProcessExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerProcessExecutor.class);
    private static final String CPU_LIMIT = "1";
    private static final long COMMAND_TIMEOUT_BUFFER_MS = 5000L;
    private static final Pattern ELAPSED_TIME_PATTERN = Pattern.compile(
            "Elapsed \\(wall clock\\) time \\(h:mm:ss or m:ss\\):\\s*(.+)"
    );
    private static final Pattern MEMORY_PATTERN = Pattern.compile(
            "Maximum resident set size \\(kbytes\\):\\s*(\\d+)"
    );

    public JudgeExecutionResult executeCompile(
            JudgeContext context,
            Path workspaceDirectory,
            String dockerImage,
            int dockerMemoryMb,
            String compileCommand,
            long compileTimeoutMs
    )
            throws IOException, InterruptedException {
        CommandResult result = runInContainer(
                context,
                workspaceDirectory,
                dockerImage,
                dockerMemoryMb,
                compileCommand,
                compileTimeoutMs + COMMAND_TIMEOUT_BUFFER_MS,
                null
        );

        if (result.systemError()) {
            return JudgeExecutionResult.systemError(result.stderr());
        }

        if (result.exitCode() != 0) {
            return JudgeExecutionResult.compilationError(result.stderr(), result.exitCode());
        }

        return new JudgeExecutionResult(true, result.stdout(), result.stderr(), 0, null, null, false, false, false, false);
    }

    public JudgeExecutionResult executeRun(
            JudgeContext context,
            Path workspaceDirectory,
            String dockerImage,
            int dockerMemoryMb,
            String runCommand
    ) throws IOException, InterruptedException {
        Path inputFile = workspaceDirectory.resolve("input.txt");
        Path outputFile = workspaceDirectory.resolve("output.txt");
        Path errorFile = workspaceDirectory.resolve("error.txt");
        Path usageFile = workspaceDirectory.resolve("usage.txt");

        Files.writeString(inputFile, context.input() == null ? "" : context.input(), StandardCharsets.UTF_8);
        Files.deleteIfExists(outputFile);
        Files.deleteIfExists(errorFile);
        Files.deleteIfExists(usageFile);

        String wrappedRunCommand = buildMeasuredRunCommand(runCommand, context.timeLimitMs());
        CommandResult result = runInContainer(
                context,
                workspaceDirectory,
                dockerImage,
                dockerMemoryMb,
                wrappedRunCommand,
                context.timeLimitMs() + COMMAND_TIMEOUT_BUFFER_MS,
                null
        );

        if (result.systemError()) {
            return JudgeExecutionResult.systemError(result.stderr());
        }

        String stdout = readWorkspaceFile(outputFile);
        String stderr = combineErrors(readWorkspaceFile(errorFile), result.stderr());
        UsageMetrics usageMetrics = parseUsageMetrics(usageFile, stderr, result.exitCode(), context.timeLimitMs());
        if (usageMetrics == null) {
            return JudgeExecutionResult.systemError("Failed to parse /usr/bin/time -v output for submission " + context.submissionId());
        }

        boolean timedOut = result.exitCode() == 124;
        boolean memoryLimitExceeded = result.exitCode() == 137 || containsDockerOom(stderr);
        return new JudgeExecutionResult(
                result.exitCode() == 0,
                stdout,
                stderr,
                result.exitCode(),
                usageMetrics.executionTimeMs(),
                usageMetrics.memoryUsageKb(),
                false,
                timedOut,
                false,
                memoryLimitExceeded
        );
    }

    private CommandResult runInContainer(
            JudgeContext context,
            Path workspaceDirectory,
            String dockerImage,
            int dockerMemoryMb,
            String containerCommand,
            long waitTimeoutMs,
            String stdin
    ) throws IOException, InterruptedException {
        Path cidFile = workspaceDirectory.resolve(".container.cid");
        List<String> command = List.of(
                "docker",
                "run",
                "--rm",
                "-i",
                "--cidfile",
                cidFile.toAbsolutePath().toString(),
                "--network",
                "none",
                "-m",
                dockerMemoryMb + "m",
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
            process = new ProcessBuilder(command).start();

            try (OutputStream outputStream = process.getOutputStream()) {
                if (stdin != null) {
                    outputStream.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            }

            boolean finished = process.waitFor(waitTimeoutMs, TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.info(
                        "Docker client process timed out for submission {} after {} ms with image={}",
                        context.submissionId(),
                        waitTimeoutMs,
                        dockerImage
                );
                return new CommandResult("", "Docker client process timed out", null, true);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();

            log.info(
                    "Docker execution finished for submission {} with image={} exitCode={} memoryLimit={}m cpuLimit={}",
                    context.submissionId(),
                    dockerImage,
                    exitCode,
                    dockerMemoryMb,
                    CPU_LIMIT
            );
            return new CommandResult(stdout, stderr, exitCode, false);
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
                log.info("Destroyed running Docker client process for submission {}", context.submissionId());
            }
            cleanupContainer(context.submissionId(), cidFile);
        }
    }

    private String buildMeasuredRunCommand(String runCommand, Integer timeLimitMs) {
        String timeoutSeconds = toTimeoutSeconds(timeLimitMs);
        return "/usr/bin/time -v -o /workspace/usage.txt timeout "
                + timeoutSeconds
                + "s "
                + runCommand
                + " < /workspace/input.txt > /workspace/output.txt 2> /workspace/error.txt";
    }

    private String toTimeoutSeconds(Integer timeLimitMs) {
        double seconds = Math.max(0.001d, timeLimitMs / 1000.0d);
        return String.format(Locale.US, "%.3f", seconds);
    }

    private UsageMetrics parseUsageMetrics(Path usageFile, String stderr, Integer exitCode, Integer timeLimitMs) throws IOException {
        if (!Files.exists(usageFile)) {
            if (Integer.valueOf(124).equals(exitCode)) {
                return new UsageMetrics(timeLimitMs, 0);
            }
            if (Integer.valueOf(137).equals(exitCode) || containsDockerOom(stderr)) {
                return new UsageMetrics(0, 0);
            }
            return null;
        }

        String usageContent = Files.readString(usageFile, StandardCharsets.UTF_8);
        Matcher elapsedMatcher = ELAPSED_TIME_PATTERN.matcher(usageContent);
        Matcher memoryMatcher = MEMORY_PATTERN.matcher(usageContent);
        if (!elapsedMatcher.find() || !memoryMatcher.find()) {
            return null;
        }

        Integer executionTimeMs = parseElapsedTimeToMillis(elapsedMatcher.group(1).trim());
        Integer memoryKb = Integer.parseInt(memoryMatcher.group(1));
        if (executionTimeMs == null || memoryKb < 0) {
            return null;
        }

        return new UsageMetrics(executionTimeMs, memoryKb);
    }

    private Integer parseElapsedTimeToMillis(String value) {
        String[] parts = value.split(":");
        if (parts.length < 2 || parts.length > 3) {
            return null;
        }

        try {
            double secondsPart = Double.parseDouble(parts[parts.length - 1]);
            int minutesPart = Integer.parseInt(parts[parts.length - 2]);
            int hoursPart = parts.length == 3 ? Integer.parseInt(parts[0]) : 0;
            double totalSeconds = secondsPart + (minutesPart * 60L) + (hoursPart * 3600L);
            return (int) Math.round(totalSeconds * 1000.0d);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String readWorkspaceFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            return "";
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private String combineErrors(String workspaceError, String dockerError) {
        if (workspaceError == null || workspaceError.isBlank()) {
            return dockerError == null ? "" : dockerError;
        }
        if (dockerError == null || dockerError.isBlank()) {
            return workspaceError;
        }
        return workspaceError + System.lineSeparator() + dockerError;
    }

    private boolean containsDockerOom(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return false;
        }

        String normalized = stderr.toLowerCase(Locale.ROOT);
        return normalized.contains("oom")
                || normalized.contains("out of memory")
                || normalized.contains("killed");
    }

    private void cleanupContainer(Long submissionId, Path cidFile) {
        if (!Files.exists(cidFile)) {
            return;
        }

        try {
            String containerId = Files.readString(cidFile, StandardCharsets.UTF_8).trim();
            if (containerId.isBlank()) {
                return;
            }

            Process cleanupProcess = new ProcessBuilder("docker", "rm", "-f", containerId).start();
            boolean finished = cleanupProcess.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                cleanupProcess.destroyForcibly();
                log.warn("Timed out while cleaning Docker container {} for submission {}", containerId, submissionId);
                return;
            }

            if (cleanupProcess.exitValue() == 0) {
                log.info("Cleaned Docker container {} for submission {}", containerId, submissionId);
                return;
            }

            String stderr = new String(cleanupProcess.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!stderr.contains("No such container")) {
                log.warn("Failed to clean Docker container {} for submission {}: {}", containerId, submissionId, stderr.trim());
            }
        } catch (Exception exception) {
            log.warn("Cleanup failed for Docker container cid file {} of submission {}", cidFile, submissionId, exception);
        } finally {
            try {
                Files.deleteIfExists(cidFile);
            } catch (IOException exception) {
                log.warn("Failed to delete cid file {} for submission {}", cidFile, submissionId, exception);
            }
        }
    }

    private record CommandResult(
            String stdout,
            String stderr,
            Integer exitCode,
            boolean systemError
    ) {
    }

    private record UsageMetrics(
            Integer executionTimeMs,
            Integer memoryUsageKb
    ) {
    }
}
