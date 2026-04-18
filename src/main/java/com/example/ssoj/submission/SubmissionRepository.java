package com.example.ssoj.submission;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Submission> findByIdForUpdate(Long id);
}
