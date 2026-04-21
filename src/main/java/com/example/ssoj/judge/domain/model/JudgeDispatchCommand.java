package com.example.ssoj.judge.domain.model;

import java.util.UUID;

public record JudgeDispatchCommand(
        UUID submissionId,
        String requestId
) {

    public static JudgeDispatchCommand fromSubmissionId(UUID submissionId) {
        return new JudgeDispatchCommand(submissionId, null);
    }
}
