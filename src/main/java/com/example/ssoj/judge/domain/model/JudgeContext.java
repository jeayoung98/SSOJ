package com.example.ssoj.judge.domain.model;

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
