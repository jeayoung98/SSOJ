package com.example.ssoj.submission.presentation.dto;

import com.example.ssoj.submission.domain.Submission;
import com.example.ssoj.submission.domain.SubmissionResult;
import com.example.ssoj.submission.domain.SubmissionStatus;

import java.time.Instant;
import java.util.UUID;

public record SubmissionResponse(
        UUID id,
        UUID userId,
        String problemId,
        String language,
        SubmissionStatus status,
        SubmissionResult result,
        Integer failedTestcaseOrder,
        Integer executionTimeMs,
        Integer memoryKb,
        Instant submittedAt,
        Instant judgedAt
) {

    public static SubmissionResponse from(Submission submission) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getUser().getId(),
                submission.getProblem().getId(),
                submission.getLanguage(),
                submission.getStatus(),
                submission.getResult(),
                submission.getFailedTestcaseOrder(),
                submission.getExecutionTimeMs(),
                submission.getMemoryKb(),
                submission.getSubmittedAt(),
                submission.getJudgedAt()
        );
    }
}
