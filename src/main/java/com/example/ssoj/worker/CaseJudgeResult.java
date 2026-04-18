package com.example.ssoj.worker;

import com.example.ssoj.submission.SubmissionStatus;

record CaseJudgeResult(
        Long testCaseId,
        SubmissionStatus status,
        Integer executionTimeMs,
        Integer memoryUsageKb
) {
}
