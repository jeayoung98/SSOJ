package com.example.ssoj.judge.domain.model;

import com.example.ssoj.submission.domain.SubmissionResult;

import java.util.UUID;

public record CaseJudgeResult(
        UUID testCaseId,
        Integer testCaseOrder,
        SubmissionResult result,
        Integer executionTimeMs,
        Integer memoryKb,
        String errorMessage
) {

    public CaseJudgeResult(
            UUID testCaseId,
            SubmissionResult result,
            Integer executionTimeMs,
            Integer memoryKb,
            String errorMessage
    ) {
        this(testCaseId, null, result, executionTimeMs, memoryKb, errorMessage);
    }
}
