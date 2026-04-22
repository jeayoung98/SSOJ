package com.example.ssoj.judge;

import com.example.ssoj.judge.infrastructure.redis.RedisJudgeDispatchService;
import com.example.ssoj.judge.domain.model.JudgeDispatchCommand;
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

    private static final Long SUBMISSION_ID = 123L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Test
    void dispatch_pushesSubmissionIdToConfiguredRedisQueue() {
        RedisJudgeDispatchService dispatchService = new RedisJudgeDispatchService(redisTemplate, "judge:queue");

        when(redisTemplate.opsForList()).thenReturn(listOperations);

        dispatchService.dispatch(new JudgeDispatchCommand(SUBMISSION_ID, "req-1"));

        verify(listOperations).leftPush("judge:queue", SUBMISSION_ID.toString());
    }
}
