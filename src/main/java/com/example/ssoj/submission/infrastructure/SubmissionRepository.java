package com.example.ssoj.submission.infrastructure;

import com.example.ssoj.submission.domain.Submission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select submission from Submission submission where submission.id = :id")
    Optional<Submission> findByIdForUpdate(@Param("id") UUID id);
}
