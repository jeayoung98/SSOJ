package com.example.ssoj.judge.domain.model;

public record JudgeExecutionResult(
        boolean success,
        String stdout,
        String stderr,
        Integer exitCode,
        Integer executionTimeMs,
        Integer memoryUsageKb,
        boolean systemError,
        boolean timedOut
) {

    public static JudgeExecutionResult notExecuted() {
        return new JudgeExecutionResult(false, "", "", null, null, null, false, false);
    }

    public static JudgeExecutionResult systemError(String stderr) {
        return new JudgeExecutionResult(false, "", stderr, null, null, null, true, false);
    }

    public static JudgeExecutionResult timeout(int executionTimeMs) {
        return new JudgeExecutionResult(false, "", "Execution timed out", null, executionTimeMs, null, false, true);
    }
}
