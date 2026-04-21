package com.example.ssoj.judge.domain.model;

import com.example.ssoj.submission.domain.SubmissionResult;

import java.util.List;

public record JudgeRunResult(
        List<CaseJudgeResult> caseResults,
        SubmissionResult finalResult,
        Integer executionTimeMs,
        Integer memoryKb
) {

    public static JudgeRunResult systemError() {
        return new JudgeRunResult(List.of(), SubmissionResult.SYSTEM_ERROR, null, null);
    }
}
