package com.example.ssoj.worker;

import com.example.ssoj.submission.SubmissionRepository;
import com.example.ssoj.submission.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@ConditionalOnProperty(name = "worker.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class JudgeWorker {

    private static final Logger log = LoggerFactory.getLogger(JudgeWorker.class);

    private final StringRedisTemplate redisTemplate;
    private final SubmissionRepository submissionRepository;

    public JudgeWorker(StringRedisTemplate redisTemplate, SubmissionRepository submissionRepository) {
        this.redisTemplate = redisTemplate;
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${worker.poll-delay-ms:1000}")
    public void poll() {
        String value = redisTemplate.opsForList().leftPop("judge:queue");
        if (value == null) {
            return;
        }

        long submissionId;
        try {
            submissionId = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            log.warn("Skipping invalid submission id from Redis: {}", value);
            return;
        }

        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            log.warn("Submission {} does not exist", submissionId);
            return;
        }

        boolean updated = submission.markAsJudging(Instant.now());
        if (updated) {
            log.info("Submission {} changed from PENDING to JUDGING", submissionId);
            return;
        }

        log.warn("Submission {} was not updated. It may not exist or is not PENDING", submissionId);
    }
}
