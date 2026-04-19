package com.example.ssoj.worker;

public record JudgeDispatchCommand(
        Long submissionId,
        String requestId
) {

    public static JudgeDispatchCommand fromSubmissionId(Long submissionId) {
        return new JudgeDispatchCommand(submissionId, null);
    }
}
