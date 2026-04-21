package com.example.ssoj.judge.domain.model;

import com.example.ssoj.submission.domain.SubmissionResult;

import java.util.UUID;

public record CaseJudgeResult(
        UUID testCaseId,
        SubmissionResult result,
        Integer executionTimeMs,
        Integer memoryKb,
        String errorMessage
) {
}
