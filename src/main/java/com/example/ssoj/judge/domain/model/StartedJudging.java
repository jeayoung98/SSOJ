package com.example.ssoj.judge.domain.model;

import java.util.List;
import java.util.UUID;

public record StartedJudging(
        UUID submissionId,
        String problemId,
        String language,
        String sourceCode,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        List<HiddenTestCaseSnapshot> hiddenTestCases
) {
}
