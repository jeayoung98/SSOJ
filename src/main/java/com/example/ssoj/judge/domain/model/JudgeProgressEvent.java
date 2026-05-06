package com.example.ssoj.judge.domain.model;

public record JudgeProgressEvent(
        Long submissionId,
        String phase,
        Integer completedTestcases,
        Integer totalTestcases,
        Integer progressPercent,
        String result
) {

    public static JudgeProgressEvent running(
            Long submissionId,
            Integer completedTestcases,
            Integer totalTestcases,
            Integer progressPercent
    ) {
        return new JudgeProgressEvent(
                submissionId,
                "RUNNING",
                completedTestcases,
                totalTestcases,
                progressPercent,
                null
        );
    }

    public static JudgeProgressEvent done(
            Long submissionId,
            Integer totalTestcases,
            String result
    ) {
        return new JudgeProgressEvent(
                submissionId,
                "DONE",
                totalTestcases,
                totalTestcases,
                100,
                result
        );
    }
}
