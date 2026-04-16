package com.example.ssoj.worker;

public record JudgeContext(
        Long submissionId,
        Long problemId,
        String language,
        String sourceCode,
        String input,
        Integer timeLimitMs,
        Integer memoryLimitMb
) {
}
