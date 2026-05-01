package com.example.ssoj.judge.executor;

import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
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
    private final long compileTimeoutMs;
    private final String compileCommand;
    private final String runCommand;
    private final DockerProcessExecutor dockerProcessExecutor;
    private final WorkspaceDirectoryFactory workspaceDirectoryFactory;

    public CppExecutor(
            @Value("${worker.executor.cpp.image:gcc:13}") String dockerImage,
            @Value("${worker.executor.compile-timeout-ms:15000}") long compileTimeoutMs,
            @Value("${worker.executor.cpp.compile-command:g++ main.cpp -O2 -std=c++17 -o main}") String compileCommand,
            @Value("${worker.executor.cpp.run-command:./main}") String runCommand,
            DockerProcessExecutor dockerProcessExecutor,
            WorkspaceDirectoryFactory workspaceDirectoryFactory
    ) {
        this.dockerImage = dockerImage;
        this.compileTimeoutMs = compileTimeoutMs;
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
    public JudgeRunResult executeSubmission(JudgeRunContext context) {
        Path tempDirectory = null;
        try {
            if (context.sourceCode() == null) {
                log.warn(
                        "C++ sourceCode is null for submission {} language={}",
                        context.submissionId(),
                        context.language()
                );
                return JudgeRunResult.systemError();
            }

            tempDirectory = workspaceDirectoryFactory.create("judge-cpp-");
            Path sourceFile = tempDirectory.resolve(SOURCE_FILE_NAME);
            Files.writeString(sourceFile, context.sourceCode(), StandardCharsets.UTF_8);
            logSourceFileState(context, tempDirectory, sourceFile);
            log.info(
                    "Executing C++ submission {} with image={}, compileCommand={}, runCommand={}",
                    context.submissionId(),
                    dockerImage,
                    compileCommand,
                    runCommand
            );
            return executeHiddenTestCases(context, tempDirectory, context.memoryLimitMb());
        } catch (IOException exception) {
            log.warn("C++ Docker execution failed for submission {}", context.submissionId(), exception);
            return JudgeRunResult.systemError();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("C++ execution interrupted for submission {}", context.submissionId(), exception);
            return JudgeRunResult.systemError();
        } catch (Exception exception) {
            log.warn("C++ execution failed for submission {}", context.submissionId(), exception);
            return JudgeRunResult.systemError();
        } finally {
            deleteDirectory(tempDirectory);
        }
    }

    private JudgeRunResult executeHiddenTestCases(JudgeRunContext context, Path workspaceDirectory, int dockerMemoryMb)
            throws IOException, InterruptedException {
        return dockerProcessExecutor.executeBatch(
                context,
                workspaceDirectory,
                dockerImage,
                dockerMemoryMb,
                compileCommand,
                compileTimeoutMs,
                runCommand
        );
    }

    private void logSourceFileState(JudgeRunContext context, Path workspaceDirectory, Path sourceFile) throws IOException {
        log.info(
                "Prepared C++ workspace for submission {} language={} workspaceDirectory={} sourceFile={} sourceExists={} sourceSizeBytes={}",
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
