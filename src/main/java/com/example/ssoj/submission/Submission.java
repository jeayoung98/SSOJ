package com.example.ssoj.submission;

import com.example.ssoj.problem.Problem;
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

import java.time.Instant;

@Entity
@Table(name = "submission")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false)
    private String language;

    @Column(name = "source_code", nullable = false, columnDefinition = "text")
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected Submission() {
    }

    public Long getId() {
        return id;
    }

    public Problem getProblem() {
        return problem;
    }

    public String getLanguage() {
        return language;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public boolean isCompleted() {
        return switch (status) {
            case AC, WA, CE, RE, TLE, MLE, SYSTEM_ERROR -> true;
            default -> false;
        };
    }

    public boolean markAsJudging(Instant startedAt) {
        if (status != SubmissionStatus.PENDING) {
            return false;
        }

        this.status = SubmissionStatus.JUDGING;
        this.startedAt = startedAt;
        return true;
    }
}
