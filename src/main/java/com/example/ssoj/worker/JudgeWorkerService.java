package com.example.ssoj.worker;

import com.example.ssoj.submission.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class JudgeWorkerService {

    private static final Logger log = LoggerFactory.getLogger(JudgeWorkerService.class);
    private static final String QUEUE_KEY = "judge:queue";

    private final StringRedisTemplate redisTemplate;
    private final SubmissionRepository submissionRepository;

    public JudgeWorkerService(StringRedisTemplate redisTemplate, SubmissionRepository submissionRepository) {
        this.redisTemplate = redisTemplate;
        this.submissionRepository = submissionRepository;
    }

    public void pollOnce() {
        String value = redisTemplate.opsForList().leftPop(QUEUE_KEY);
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

        boolean updated = submissionRepository.markAsJudging(submissionId);
        if (updated) {
            log.info("Submission {} changed from PENDING to JUDGING", submissionId);
            return;
        }

        log.warn("Submission {} was not updated. It may not exist or is not PENDING", submissionId);
    }
}
