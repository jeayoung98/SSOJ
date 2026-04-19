package com.example.ssoj.worker;

public record RunnerExecutionRequest(
        Long submissionId,
        Long problemId,
        String language,
        String sourceCode,
        String input,
        Integer timeLimitMs,
        Integer memoryLimitMb
) {

    static RunnerExecutionRequest from(JudgeContext context) {
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
