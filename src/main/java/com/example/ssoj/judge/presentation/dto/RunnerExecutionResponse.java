package com.example.ssoj.judge.presentation.dto;

import com.example.ssoj.judge.domain.model.JudgeExecutionResult;

public record RunnerExecutionResponse(
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

    public static RunnerExecutionResponse from(JudgeExecutionResult result) {
        return new RunnerExecutionResponse(
                result.success(),
                result.stdout(),
                result.stderr(),
                result.exitCode(),
                result.executionTimeMs(),
                result.memoryUsageKb(),
                result.systemError(),
                result.timedOut(),
                result.compilationError(),
                result.memoryLimitExceeded()
        );
    }

    public static RunnerExecutionResponse systemError(String stderr) {
        return from(JudgeExecutionResult.systemError(stderr));
    }
}
