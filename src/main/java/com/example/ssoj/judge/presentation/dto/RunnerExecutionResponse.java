package com.example.ssoj.judge.presentation.dto;

import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record RunnerExecutionResponse(
        SubmissionResult result,
        Integer executionTimeMs,
        Integer memoryUsageKb,
        Integer failedTestcaseOrder
) {

    private static final Logger log = LoggerFactory.getLogger(RunnerExecutionResponse.class);

    public static RunnerExecutionResponse from(JudgeRunResult result) {
        return from(null, result);
    }

    public static RunnerExecutionResponse from(Long submissionId, JudgeRunResult result) {
        log.info(
                "Creating runner execution response submissionId={} result={} executionTimeMs={} memoryKb={} failedTestcaseOrder={}",
                submissionId,
                result.finalResult(),
                result.executionTimeMs(),
                result.memoryKb(),
                result.failedTestcaseOrder()
        );
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
