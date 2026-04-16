package com.example.ssoj.worker;

public record JudgeExecutionResult(
        boolean success,
        String stdout,
        String stderr,
        Integer executionTimeMs,
        Integer memoryUsageKb
) {

    public static JudgeExecutionResult notExecuted() {
        return new JudgeExecutionResult(false, "", "", null, null);
    }
}
