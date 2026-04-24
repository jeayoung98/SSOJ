package com.example.ssoj.judge.presentation.dto;

import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeRunContext;

import java.util.List;

public record RunnerExecutionRequest(
        Long submissionId,
        Long problemId,
        String language,
        String sourceCode,
        List<RunnerTestCaseRequest> testCases,
        Integer timeLimitMs,
        Integer memoryLimitMb
) {

    public static RunnerExecutionRequest from(JudgeRunContext context) {
        return new RunnerExecutionRequest(
                context.submissionId(),
                context.problemId(),
                context.language(),
                context.sourceCode(),
                context.hiddenTestCases().stream()
                        .map(RunnerTestCaseRequest::from)
                        .toList(),
                context.timeLimitMs(),
                context.memoryLimitMb()
        );
    }

    public List<HiddenTestCaseSnapshot> toHiddenTestCases() {
        if (testCases == null) {
            return List.of();
        }

        return testCases.stream()
                .map(testCase -> new HiddenTestCaseSnapshot(
                        null,
                        testCase.testCaseOrder(),
                        testCase.input(),
                        testCase.expectedOutput()
                ))
                .toList();
    }
}
