package com.example.ssoj.submission;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SubmissionRepository {

    private final JdbcTemplate jdbcTemplate;

    public SubmissionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean markAsJudging(long submissionId) {
        int updatedRows = jdbcTemplate.update(
                """
                update submission
                set status = ?
                where id = ?
                  and status = ?
                """,
                SubmissionStatus.JUDGING.name(),
                submissionId,
                SubmissionStatus.PENDING.name()
        );

        return updatedRows > 0;
    }
}
