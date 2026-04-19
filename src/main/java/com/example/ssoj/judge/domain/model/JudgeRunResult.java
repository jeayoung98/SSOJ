package com.example.ssoj.judge.domain.model;

import com.example.ssoj.submission.domain.SubmissionStatus;

import java.util.List;

public record JudgeRunResult(
        List<CaseJudgeResult> caseResults,
        SubmissionStatus finalStatus
) {

    public static JudgeRunResult systemError() {
        return new JudgeRunResult(List.of(), SubmissionStatus.SYSTEM_ERROR);
    }
}
