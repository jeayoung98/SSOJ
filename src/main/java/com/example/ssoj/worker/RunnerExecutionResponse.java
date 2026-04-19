package com.example.ssoj.worker;

public record RunnerExecutionResponse(
        boolean success,
        String stdout,
        String stderr,
        Integer exitCode,
        Integer executionTimeMs,
        Integer memoryUsageKb,
        boolean systemError,
        boolean timedOut
) {
}
