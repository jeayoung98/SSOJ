package com.example.ssoj.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "worker.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class JudgeQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(JudgeQueueConsumer.class);
    private static final String QUEUE_KEY = "judge:queue";

    private final StringRedisTemplate redisTemplate;
    private final JudgeService judgeService;

    public JudgeQueueConsumer(StringRedisTemplate redisTemplate, JudgeService judgeService) {
        this.redisTemplate = redisTemplate;
        this.judgeService = judgeService;
    }

    @Scheduled(fixedDelayString = "${worker.poll-delay-ms:1000}")
    public void consume() {
        String value = redisTemplate.opsForList().leftPop(QUEUE_KEY);
        if (value == null) {
            return;
        }

        try {
            Long submissionId = Long.parseLong(value);
            log.info("Received submissionId={} from Redis queue {}", submissionId, QUEUE_KEY);
            judgeService.judge(submissionId);
        } catch (NumberFormatException exception) {
            log.warn("Skipping invalid submission id from Redis: {}", value);
        }
    }
}
