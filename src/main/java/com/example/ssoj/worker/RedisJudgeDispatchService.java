package com.example.ssoj.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "judge.dispatch.mode", havingValue = "redis", matchIfMissing = true)
public class RedisJudgeDispatchService implements JudgeDispatchPort {

    private final StringRedisTemplate redisTemplate;
    private final String queueKey;

    public RedisJudgeDispatchService(
            StringRedisTemplate redisTemplate,
            @Value("${judge.dispatch.redis.queue-key:judge:queue}") String queueKey
    ) {
        this.redisTemplate = redisTemplate;
        this.queueKey = queueKey;
    }

    @Override
    public void dispatch(JudgeDispatchCommand command) {
        redisTemplate.opsForList().leftPush(queueKey, command.submissionId().toString());
    }
}
