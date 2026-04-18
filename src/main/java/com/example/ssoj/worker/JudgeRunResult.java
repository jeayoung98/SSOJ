package com.example.ssoj.worker;

import com.example.ssoj.submission.SubmissionStatus;

import java.util.List;

record JudgeRunResult(
        List<CaseJudgeResult> caseResults,
        SubmissionStatus finalStatus
) {

    static JudgeRunResult systemError() {
        return new JudgeRunResult(List.of(), SubmissionStatus.SYSTEM_ERROR);
    }
}
