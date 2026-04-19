package com.example.ssoj.judge.domain.model;

public record HiddenTestCaseSnapshot(
        Long testCaseId,
        String input,
        String expectedOutput
) {
}
