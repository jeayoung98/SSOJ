package com.example.ssoj.worker;

import com.example.ssoj.submission.Submission;
import com.example.ssoj.submission.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);
    private final SubmissionRepository submissionRepository;

    public JudgeService(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public void judge(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            log.warn("Submission {} does not exist", submissionId);
            return;
        }

        if (submission.isCompleted()) {
            log.info("Submission {} is already completed with status={}", submissionId, submission.getStatus());
            return;
        }

        boolean updated = submission.markAsJudging(Instant.now());
        if (!updated) {
            log.info("Submission {} is ignored because current status={}", submissionId, submission.getStatus());
            return;
        }

        log.info("Submission {} changed from PENDING to JUDGING", submissionId);
    }
}
