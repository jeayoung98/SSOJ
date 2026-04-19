package com.example.ssoj.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisJudgeDispatchServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Test
    void dispatch_pushesSubmissionIdToConfiguredRedisQueue() {
        RedisJudgeDispatchService dispatchService = new RedisJudgeDispatchService(redisTemplate, "judge:queue");

        when(redisTemplate.opsForList()).thenReturn(listOperations);

        dispatchService.dispatch(new JudgeDispatchCommand(123L, "req-1"));

        verify(listOperations).leftPush("judge:queue", "123");
    }
}
