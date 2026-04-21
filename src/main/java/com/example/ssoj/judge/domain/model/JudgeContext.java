package com.example.ssoj.judge.domain.model;

import java.util.UUID;

public record JudgeContext(
        UUID submissionId,
        String problemId,
        String language,
        String sourceCode,
        String input,
        Integer timeLimitMs,
        Integer memoryLimitMb
) {
}
