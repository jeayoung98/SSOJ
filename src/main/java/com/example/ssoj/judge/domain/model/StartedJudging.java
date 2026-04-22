package com.example.ssoj.judge.domain.model;

import java.util.List;

public record StartedJudging(
        Long submissionId,
        Long problemId,
        String language,
        String sourceCode,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        List<HiddenTestCaseSnapshot> hiddenTestCases
) {
}
