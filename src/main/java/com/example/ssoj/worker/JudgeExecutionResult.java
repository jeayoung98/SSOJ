package com.example.ssoj.worker;

public record JudgeExecutionResult(
        boolean success,
        String stdout,
        String stderr,
        Integer executionTimeMs,
        Integer memoryUsageKb,
        boolean systemError
) {

    public static JudgeExecutionResult notExecuted() {
        return new JudgeExecutionResult(false, "", "", null, null, false);
    }

    public static JudgeExecutionResult systemError(String stderr) {
        return new JudgeExecutionResult(false, "", stderr, null, null, true);
    }
}
