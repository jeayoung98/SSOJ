package com.example.ssoj.submission.infrastructure;

import com.example.ssoj.submission.domain.SubmissionTestcaseResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubmissionTestcaseResultRepository extends JpaRepository<SubmissionTestcaseResult, UUID> {
}
