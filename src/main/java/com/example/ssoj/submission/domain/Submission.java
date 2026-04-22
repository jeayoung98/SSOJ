package com.example.ssoj.submission.domain;

import com.example.ssoj.problem.domain.Problem;
import com.example.ssoj.user.domain.User;
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

import java.time.Instant;

@Getter
@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false)
    private String language;

    @Column(name = "source_code", nullable = false, columnDefinition = "text")
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "submission_status")
    private SubmissionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "submission_result")
    private SubmissionResult result;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "memory_kb")
    private Integer memoryKb;

    @Column(name = "failed_testcase_order")
    private Integer failedTestcaseOrder;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "judged_at")
    private Instant judgedAt;

    protected Submission() {
    }

    public boolean isCompleted() {
        return result != null;
    }

    public boolean markAsJudging() {
        if (status != SubmissionStatus.PENDING) {
            return false;
        }

        this.status = SubmissionStatus.JUDGING;
        return true;
    }

    public void finish(SubmissionResult result, Integer executionTimeMs, Integer memoryKb, Instant judgedAt) {
        finish(result, executionTimeMs, memoryKb, null, judgedAt);
    }

    public void finish(
            SubmissionResult result,
            Integer executionTimeMs,
            Integer memoryKb,
            Integer failedTestcaseOrder,
            Instant judgedAt
    ) {
        this.status = SubmissionStatus.DONE;
        this.result = result;
        this.executionTimeMs = executionTimeMs;
        this.memoryKb = memoryKb;
        this.failedTestcaseOrder = failedTestcaseOrder;
        this.judgedAt = judgedAt;
    }
}
