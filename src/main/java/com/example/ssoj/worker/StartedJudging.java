package com.example.ssoj.worker;

import java.util.List;

record StartedJudging(
        Long submissionId,
        Long problemId,
        String language,
        String sourceCode,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        List<HiddenTestCaseSnapshot> hiddenTestCases
) {
}
