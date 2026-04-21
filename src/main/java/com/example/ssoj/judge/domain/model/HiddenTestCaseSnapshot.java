package com.example.ssoj.judge.domain.model;

import java.util.UUID;

public record HiddenTestCaseSnapshot(
        UUID testCaseId,
        Integer testCaseOrder,
        String input,
        String expectedOutput
) {

    public HiddenTestCaseSnapshot(
            UUID testCaseId,
            String input,
            String expectedOutput
    ) {
        this(testCaseId, null, input, expectedOutput);
    }
}
