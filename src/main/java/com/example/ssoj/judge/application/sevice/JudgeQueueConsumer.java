package com.example.ssoj.judge.application.sevice;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.UUID;

@ConditionalOnProperty(name = "worker.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "worker.mode", havingValue = "redis-polling", matchIfMissing = true)
@ConditionalOnProperty(name = "worker.role", havingValue = "orchestrator", matchIfMissing = true)
@Component
public class JudgeQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(JudgeQueueConsumer.class);

    private final StringRedisTemplate redisTemplate;
    private final JudgeService judgeService;
    private final ExecutorService executorService;
    private final Semaphore semaphore;
    private final String queueKey;

    @Autowired
    public JudgeQueueConsumer(
            StringRedisTemplate redisTemplate,
            JudgeService judgeService,
            @Value("${worker.max-concurrency:2}") int maxConcurrency,
            @Value("${judge.dispatch.redis.queue-key:judge:queue}") String queueKey
    ) {
        this.redisTemplate = redisTemplate;
        this.judgeService = judgeService;
        this.executorService = Executors.newFixedThreadPool(maxConcurrency);
        this.semaphore = new Semaphore(maxConcurrency);
        this.queueKey = queueKey;
    }

    public JudgeQueueConsumer(
            StringRedisTemplate redisTemplate,
            JudgeService judgeService,
            ExecutorService executorService,
            Semaphore semaphore,
            String queueKey
    ) {
        this.redisTemplate = redisTemplate;
        this.judgeService = judgeService;
        this.executorService = executorService;
        this.semaphore = semaphore;
        this.queueKey = queueKey;
    }

    @Scheduled(fixedDelayString = "${worker.poll-delay-ms:1000}")
    public void consume() {
        if (!semaphore.tryAcquire()) {
            return;
        }

        String value;
        try {
            value = redisTemplate.opsForList().leftPop(queueKey);
        } catch (RedisConnectionFailureException exception) {
            semaphore.release();
            log.warn("Failed to connect to Redis while reading queue {}", queueKey, exception);
            return;
        }

        if (value == null) {
            semaphore.release();
            return;
        }

        try {
            UUID submissionId = UUID.fromString(value);
            log.info("Received submissionId={} from Redis queue {}", submissionId, queueKey);
            executorService.submit(() -> {
                try {
                    judgeService.judge(submissionId);
                } finally {
                    semaphore.release();
                }
            });
        } catch (IllegalArgumentException exception) {
            semaphore.release();
            log.warn("Skipping invalid submission id from Redis: {}", value);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}
