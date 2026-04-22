package com.example.ssoj.judge.domain.model;

import com.example.ssoj.submission.domain.SubmissionResult;

public record JudgeRunResult(
        SubmissionResult finalResult,
        Integer executionTimeMs,
        Integer memoryKb,
        Integer failedTestcaseOrder
) {

    public JudgeRunResult(
            SubmissionResult finalResult,
            Integer executionTimeMs,
            Integer memoryKb
    ) {
        this(finalResult, executionTimeMs, memoryKb, null);
    }

    public static JudgeRunResult systemError() {
        return new JudgeRunResult(SubmissionResult.SYSTEM_ERROR, null, null);
    }
}
