package com.example.ssoj.judge.domain.model;

import com.example.ssoj.submission.domain.SubmissionStatus;

public record CaseJudgeResult(
        Long testCaseId,
        SubmissionStatus status,
        Integer executionTimeMs,
        Integer memoryUsageKb
) {
}
