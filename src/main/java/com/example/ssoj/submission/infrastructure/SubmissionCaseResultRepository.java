package com.example.ssoj.submission.infrastructure;

import com.example.ssoj.submission.domain.SubmissionCaseResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionCaseResultRepository extends JpaRepository<SubmissionCaseResult, Long> {
}
