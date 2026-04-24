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
public class PythonExecutor implements LanguageExecutor {

    private static final Logger log = LoggerFactory.getLogger(PythonExecutor.class);
    private static final String SOURCE_FILE_NAME = "main.py";
    private static final int MIN_DOCKER_MEMORY_MB = 128;

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
    public JudgeRunResult executeSubmission(JudgeRunContext context) {
        Path tempDirectory = null;
        try {
            if (context.sourceCode() == null) {
                log.warn(
                        "Python sourceCode is null for submission {} language={}",
                        context.submissionId(),
                        context.language()
                );
                return JudgeRunResult.systemError();
            }

            tempDirectory = workspaceDirectoryFactory.create("judge-python-");
            Path sourceFile = tempDirectory.resolve(SOURCE_FILE_NAME);
            Files.writeString(sourceFile, context.sourceCode(), StandardCharsets.UTF_8);
            logSourceFileState(context, tempDirectory, sourceFile);

            return executeHiddenTestCases(context, tempDirectory, Math.max(context.memoryLimitMb(), MIN_DOCKER_MEMORY_MB));
        } catch (IOException exception) {
            log.warn("Python Docker execution failed for submission {}", context.submissionId(), exception);
            return JudgeRunResult.systemError();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Python execution interrupted for submission {}", context.submissionId(), exception);
            return JudgeRunResult.systemError();
        } catch (Exception exception) {
            log.warn("Python execution failed for submission {}", context.submissionId(), exception);
            return JudgeRunResult.systemError();
        } finally {
            deleteDirectory(tempDirectory);
        }
    }

    private JudgeRunResult executeHiddenTestCases(JudgeRunContext context, Path workspaceDirectory, int dockerMemoryMb)
            throws IOException, InterruptedException {
        Integer maxExecutionTimeMs = null;
        Integer maxMemoryKb = null;

        for (HiddenTestCaseSnapshot testCase : context.hiddenTestCases()) {
            JudgeExecutionResult executionResult = dockerProcessExecutor.executeRun(
                    testCaseContext(context, testCase.input()),
                    workspaceDirectory,
                    dockerImage,
                    dockerMemoryMb,
                    "python3 main.py"
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
                "Prepared Python workspace for submission {} language={} workspaceDirectory={} sourceFile={} sourceExists={} sourceSizeBytes={}",
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
