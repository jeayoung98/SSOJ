package com.example.ssoj.judge.executor;

import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionPolicy;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class JavaExecutor implements LanguageExecutor {

    private static final Logger log = LoggerFactory.getLogger(JavaExecutor.class);
    private static final String SOURCE_FILE_NAME = "Main.java";
    private static final int MIN_DOCKER_MEMORY_MB = 256;

    private final String dockerImage;
    private final long compileTimeoutMs;
    private final DockerProcessExecutor dockerProcessExecutor;
    private final WorkspaceDirectoryFactory workspaceDirectoryFactory;

    public JavaExecutor(
            @Value("${worker.executor.java.image:eclipse-temurin:17-jdk}") String dockerImage,
            @Value("${worker.executor.compile-timeout-ms:15000}") long compileTimeoutMs,
            DockerProcessExecutor dockerProcessExecutor,
            WorkspaceDirectoryFactory workspaceDirectoryFactory
    ) {
        this.dockerImage = dockerImage;
        this.compileTimeoutMs = compileTimeoutMs;
        this.dockerProcessExecutor = dockerProcessExecutor;
        this.workspaceDirectoryFactory = workspaceDirectoryFactory;
    }

    @Override
    public boolean supports(String language) {
        return "java".equalsIgnoreCase(language);
    }

    @Override
    public JudgeRunResult executeSubmission(JudgeRunContext context) {
        Path tempDirectory = null;
        try {
            if (context.sourceCode() == null) {
                log.warn(
                        "Java sourceCode is null for submission {} language={}",
                        context.submissionId(),
                        context.language()
                );
                return JudgeRunResult.systemError();
            }

            tempDirectory = workspaceDirectoryFactory.create("judge-java-");
            Path sourceFile = tempDirectory.resolve(SOURCE_FILE_NAME);
            Files.writeString(sourceFile, context.sourceCode(), StandardCharsets.UTF_8);
            logSourceFileState(context, tempDirectory, sourceFile);

            int dockerMemoryMb = resolveDockerMemoryMb(context.memoryLimitMb());
            JudgeExecutionResult compileResult = dockerProcessExecutor.executeCompile(
                    testCaseContext(context, ""),
                    tempDirectory,
                    dockerImage,
                    dockerMemoryMb,
                    "javac Main.java",
                    compileTimeoutMs
            );
            if (!compileResult.success()) {
                return compileFailureResult(compileResult);
            }

            return executeHiddenTestCases(context, tempDirectory, dockerMemoryMb);
        } catch (IOException exception) {
            log.warn("Java Docker execution failed for submission {}", context.submissionId(), exception);
            return JudgeRunResult.systemError();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Java execution interrupted for submission {}", context.submissionId(), exception);
            return JudgeRunResult.systemError();
        } catch (Exception exception) {
            log.warn("Java execution failed for submission {}", context.submissionId(), exception);
            return JudgeRunResult.systemError();
        } finally {
            deleteDirectory(tempDirectory);
        }
    }

    private int resolveDockerMemoryMb(Integer memoryLimitMb) {
        return Math.max(memoryLimitMb * 2, MIN_DOCKER_MEMORY_MB);
    }

    private String buildRunCommand(JudgeContext context) {
        return "java -Xmx" + context.memoryLimitMb() + "m Main";
    }

    private JudgeRunResult executeHiddenTestCases(JudgeRunContext context, Path workspaceDirectory, int dockerMemoryMb)
            throws IOException, InterruptedException {
        Integer maxExecutionTimeMs = null;
        Integer maxMemoryKb = null;
        String runCommand = buildRunCommand(new JudgeContext(
                context.submissionId(),
                context.problemId(),
                context.language(),
                context.sourceCode(),
                "",
                context.timeLimitMs(),
                context.memoryLimitMb()
        ));

        for (HiddenTestCaseSnapshot testCase : context.hiddenTestCases()) {
            JudgeExecutionResult executionResult = dockerProcessExecutor.executeRun(
                    testCaseContext(context, testCase.input()),
                    workspaceDirectory,
                    dockerImage,
                    dockerMemoryMb,
                    runCommand
            );
            SubmissionResult caseResult = JudgeExecutionPolicy.determineCaseResult(
                    context.language(),
                    executionResult,
                    testCase.expectedOutput(),
                    context.memoryLimitMb()
            );
            if (caseResult != SubmissionResult.AC) {
                return new JudgeRunResult(
                        caseResult,
                        executionResult.executionTimeMs(),
                        executionResult.memoryUsageKb(),
                        JudgeExecutionPolicy.hasFailedTestcaseOrder(caseResult) ? testCase.testCaseOrder() : null
                );
            }

            maxExecutionTimeMs = max(maxExecutionTimeMs, executionResult.executionTimeMs());
            maxMemoryKb = max(maxMemoryKb, executionResult.memoryUsageKb());
        }

        return new JudgeRunResult(SubmissionResult.AC, maxExecutionTimeMs, maxMemoryKb, null);
    }

    private JudgeContext testCaseContext(JudgeRunContext context, String input) {
        return new JudgeContext(
                context.submissionId(),
                context.problemId(),
                context.language(),
                context.sourceCode(),
                input,
                context.timeLimitMs(),
                context.memoryLimitMb()
        );
    }

    private JudgeRunResult compileFailureResult(JudgeExecutionResult compileResult) {
        if (compileResult.compilationError()) {
            return new JudgeRunResult(SubmissionResult.CE, null, null, null);
        }
        return JudgeRunResult.systemError();
    }

    private Integer max(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return Math.max(current, candidate);
    }

    private void logSourceFileState(JudgeRunContext context, Path workspaceDirectory, Path sourceFile) throws IOException {
        log.info(
                "Prepared Java workspace for submission {} language={} workspaceDirectory={} sourceFile={} sourceExists={} sourceSizeBytes={}",
                context.submissionId(),
                context.language(),
                workspaceDirectory.toAbsolutePath(),
                sourceFile.toAbsolutePath(),
                Files.exists(sourceFile),
                Files.size(sourceFile)
        );
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
