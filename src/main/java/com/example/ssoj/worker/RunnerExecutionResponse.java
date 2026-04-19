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

    static RunnerExecutionResponse from(JudgeExecutionResult result) {
        return new RunnerExecutionResponse(
                result.success(),
                result.stdout(),
                result.stderr(),
                result.exitCode(),
                result.executionTimeMs(),
                result.memoryUsageKb(),
                result.systemError(),
                result.timedOut()
        );
    }

    static RunnerExecutionResponse systemError(String stderr) {
        return from(JudgeExecutionResult.systemError(stderr));
    }
}
