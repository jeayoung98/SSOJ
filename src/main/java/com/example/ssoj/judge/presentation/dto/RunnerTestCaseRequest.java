package com.example.ssoj.judge.presentation.dto;

import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;

public record RunnerTestCaseRequest(
        Integer testCaseOrder,
        String input,
        String expectedOutput
) {

    public static RunnerTestCaseRequest from(HiddenTestCaseSnapshot testCase) {
        return new RunnerTestCaseRequest(
                testCase.testCaseOrder(),
                testCase.input(),
                testCase.expectedOutput()
        );
    }
}
