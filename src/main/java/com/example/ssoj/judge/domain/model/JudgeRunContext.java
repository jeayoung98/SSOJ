package com.example.ssoj.judge.domain.model;

import java.util.List;

public record JudgeRunContext(
        Long submissionId,
        Long problemId,
        String language,
        String sourceCode,
        List<HiddenTestCaseSnapshot> hiddenTestCases,
        Integer timeLimitMs,
        Integer memoryLimitMb
) {
}
