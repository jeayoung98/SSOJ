package com.example.ssoj.judge.domain.model;

import com.example.ssoj.submission.domain.SubmissionResult;

import java.util.Arrays;
import java.util.List;

public final class JudgeExecutionPolicy {

    private JudgeExecutionPolicy() {
    }

    public static SubmissionResult determineCaseResult(
            String language,
            JudgeExecutionResult executionResult,
            String expectedOutput,
            Integer memoryLimitMb
    ) {
        if (executionResult.systemError()) {
            return SubmissionResult.SYSTEM_ERROR;
        }

        if (executionResult.compilationError()) {
            return SubmissionResult.CE;
        }

        if (executionResult.timedOut()) {
            return SubmissionResult.TLE;
        }

        if (isMemoryLimitExceeded(language, executionResult, memoryLimitMb)) {
            return SubmissionResult.MLE;
        }

        if (!executionResult.success()) {
            return SubmissionResult.RE;
        }

        if (!matchesExpectedOutput(executionResult.stdout(), expectedOutput)) {
            return SubmissionResult.WA;
        }

        return SubmissionResult.AC;
    }

    public static boolean hasFailedTestcaseOrder(SubmissionResult result) {
        return result == SubmissionResult.WA
                || result == SubmissionResult.TLE
                || result == SubmissionResult.RE
                || result == SubmissionResult.MLE;
    }

    public static boolean isMemoryLimitExceeded(
            String language,
            JudgeExecutionResult executionResult,
            Integer memoryLimitMb
    ) {
        if (memoryLimitMb == null) {
            return false;
        }

        if (executionResult.memoryLimitExceeded()) {
            return true;
        }

        if ("java".equalsIgnoreCase(language)) {
            return Integer.valueOf(137).equals(executionResult.exitCode());
        }

        if (executionResult.memoryUsageKb() != null) {
            return executionResult.memoryUsageKb() > memoryLimitMb * 1024;
        }

        return Integer.valueOf(137).equals(executionResult.exitCode());
    }

    public static boolean matchesExpectedOutput(String actualOutput, String expectedOutput) {
        List<String> actualLines = normalizeLines(actualOutput);
        List<String> expectedLines = normalizeLines(expectedOutput);
        return actualLines.equals(expectedLines);
    }

    private static List<String> normalizeLines(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        return Arrays.stream(output.trim().split("\\R"))
                .map(String::trim)
                .toList();
    }
}
