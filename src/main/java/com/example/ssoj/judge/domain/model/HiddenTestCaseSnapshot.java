package com.example.ssoj.judge.domain.model;

import java.util.UUID;

public record HiddenTestCaseSnapshot(
        UUID testCaseId,
        String input,
        String expectedOutput
) {
}
