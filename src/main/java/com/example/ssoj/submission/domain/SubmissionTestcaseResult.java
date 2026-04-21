package com.example.ssoj.submission.domain;

import com.example.ssoj.testcase.domain.ProblemTestcase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

@Getter
@Entity
@Table(name = "submission_testcase_results")
public class SubmissionTestcaseResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "testcase_id", nullable = false)
    private ProblemTestcase testcase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "submission_result")
    private SubmissionResult result;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "memory_kb")
    private Integer memoryKb;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected SubmissionTestcaseResult() {
    }

    public SubmissionTestcaseResult(
            Submission submission,
            ProblemTestcase testcase,
            SubmissionResult result,
            Integer executionTimeMs,
            Integer memoryKb,
            String errorMessage
    ) {
        this.submission = submission;
        this.testcase = testcase;
        this.result = result;
        this.executionTimeMs = executionTimeMs;
        this.memoryKb = memoryKb;
        this.errorMessage = errorMessage;
    }
}
