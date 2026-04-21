package com.example.ssoj.judge.presentation.dto;

import com.example.ssoj.judge.domain.model.JudgeContext;

import java.util.UUID;

public record RunnerExecutionRequest(
        UUID submissionId,
        String problemId,
        String language,
        String sourceCode,
        String input,
        Integer timeLimitMs,
        Integer memoryLimitMb
) {

    public static RunnerExecutionRequest from(JudgeContext context) {
        return new RunnerExecutionRequest(
                context.submissionId(),
                context.problemId(),
                context.language(),
                context.sourceCode(),
                context.input(),
                context.timeLimitMs(),
                context.memoryLimitMb()
        );
    }
}
