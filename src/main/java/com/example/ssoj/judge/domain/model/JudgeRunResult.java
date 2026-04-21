package com.example.ssoj.judge.domain.model;

import com.example.ssoj.submission.domain.SubmissionResult;

import java.util.List;

public record JudgeRunResult(
        List<CaseJudgeResult> caseResults,
        SubmissionResult finalResult,
        Integer executionTimeMs,
        Integer memoryKb,
        Integer failedTestcaseOrder
) {

    public JudgeRunResult(
            List<CaseJudgeResult> caseResults,
            SubmissionResult finalResult,
            Integer executionTimeMs,
            Integer memoryKb
    ) {
        this(caseResults, finalResult, executionTimeMs, memoryKb, null);
    }

    public static JudgeRunResult systemError() {
        return new JudgeRunResult(List.of(), SubmissionResult.SYSTEM_ERROR, null, null);
    }
}
