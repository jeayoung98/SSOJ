package com.example.ssoj.judge.domain.model;

public record JudgeExecutionResult(
        boolean success,
        String stdout,
        String stderr,
        Integer exitCode,
        Integer executionTimeMs,
        Integer memoryUsageKb,
        boolean systemError,
        boolean timedOut,
        boolean compilationError,
        boolean memoryLimitExceeded
) {

    public static JudgeExecutionResult notExecuted() {
        return new JudgeExecutionResult(false, "", "", null, null, null, false, false, false, false);
    }

    public static JudgeExecutionResult systemError(String stderr) {
        return new JudgeExecutionResult(false, "", stderr, null, null, null, true, false, false, false);
    }

    public static JudgeExecutionResult timeout(int executionTimeMs) {
        return new JudgeExecutionResult(false, "", "Execution timed out", 124, executionTimeMs, null, false, true, false, false);
    }

    public static JudgeExecutionResult compilationError(String stderr, Integer exitCode) {
        return new JudgeExecutionResult(false, "", stderr, exitCode, null, null, false, false, true, false);
    }
}
