package com.example.ssoj.judge.presentation.dto;

import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.submission.domain.SubmissionResult;

public record RunnerExecutionResponse(
        SubmissionResult result,
        Integer executionTimeMs,
        Integer memoryUsageKb,
        Integer failedTestcaseOrder
) {

    public static RunnerExecutionResponse from(JudgeRunResult result) {
        return new RunnerExecutionResponse(
                result.finalResult(),
                result.executionTimeMs(),
                result.memoryKb(),
                result.failedTestcaseOrder()
        );
    }

    public static RunnerExecutionResponse systemError() {
        return from(JudgeRunResult.systemError());
    }
}
