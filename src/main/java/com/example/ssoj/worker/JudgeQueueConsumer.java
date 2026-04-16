package com.example.ssoj.worker;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@ConditionalOnProperty(name = "worker.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class JudgeQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(JudgeQueueConsumer.class);
    private static final String QUEUE_KEY = "judge:queue";

    private final StringRedisTemplate redisTemplate;
    private final JudgeService judgeService;
    private final ExecutorService executorService;
    private final Semaphore semaphore;

    public JudgeQueueConsumer(
            StringRedisTemplate redisTemplate,
            JudgeService judgeService,
            @Value("${worker.max-concurrency:2}") int maxConcurrency
    ) {
        this.redisTemplate = redisTemplate;
        this.judgeService = judgeService;
        this.executorService = Executors.newFixedThreadPool(maxConcurrency);
        this.semaphore = new Semaphore(maxConcurrency);
    }

    @Scheduled(fixedDelayString = "${worker.poll-delay-ms:1000}")
    public void consume() {
        if (!semaphore.tryAcquire()) {
            return;
        }

        String value;
        try {
            value = redisTemplate.opsForList().leftPop(QUEUE_KEY);
        } catch (RedisConnectionFailureException exception) {
            semaphore.release();
            log.warn("Failed to connect to Redis while reading queue {}", QUEUE_KEY, exception);
            return;
        }

        if (value == null) {
            semaphore.release();
            return;
        }

        try {
            Long submissionId = Long.parseLong(value);
            log.info("Received submissionId={} from Redis queue {}", submissionId, QUEUE_KEY);
            executorService.submit(() -> {
                try {
                    judgeService.judge(submissionId);
                } finally {
                    semaphore.release();
                }
            });
        } catch (NumberFormatException exception) {
            semaphore.release();
            log.warn("Skipping invalid submission id from Redis: {}", value);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}
