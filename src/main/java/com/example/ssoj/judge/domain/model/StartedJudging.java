package com.example.ssoj.judge.domain.model;

import java.util.List;

public record StartedJudging(
        Long submissionId,
        Long problemId,
        String language,
        String sourceCode,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        List<HiddenTestCaseSnapshot> hiddenTestCases,
        int hiddenTestcaseCount
) {

    public StartedJudging(
            Long submissionId,
            Long problemId,
            String language,
            String sourceCode,
            Integer timeLimitMs,
            Integer memoryLimitMb,
            List<HiddenTestCaseSnapshot> hiddenTestCases
    ) {
        this(
                submissionId,
                problemId,
                language,
                sourceCode,
                timeLimitMs,
                memoryLimitMb,
                hiddenTestCases,
                hiddenTestCases == null ? 0 : hiddenTestCases.size()
        );
    }
}
