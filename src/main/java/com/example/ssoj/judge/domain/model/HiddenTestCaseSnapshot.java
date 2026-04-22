package com.example.ssoj.judge.domain.model;

public record HiddenTestCaseSnapshot(
        Long testCaseId,
        Integer testCaseOrder,
        String input,
        String expectedOutput
) {

    public HiddenTestCaseSnapshot(
            Long testCaseId,
            String input,
            String expectedOutput
    ) {
        this(testCaseId, null, input, expectedOutput);
    }
}
