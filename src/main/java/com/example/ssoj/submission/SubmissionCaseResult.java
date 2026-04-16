package com.example.ssoj.submission;

import com.example.ssoj.testcase.TestCase;
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

@Entity
@Table(name = "submission_case_result")
public class SubmissionCaseResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "memory_usage_kb")
    private Integer memoryUsageKb;

    protected SubmissionCaseResult() {
    }

    public Long getId() {
        return id;
    }

    public Submission getSubmission() {
        return submission;
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public Integer getExecutionTimeMs() {
        return executionTimeMs;
    }

    public Integer getMemoryUsageKb() {
        return memoryUsageKb;
    }
}
