package com.example.ssoj.judge;

import com.example.ssoj.judge.application.sevice.JudgeQueueConsumer;
import com.example.ssoj.judge.application.sevice.JudgeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeQueueConsumerTest {

    private static final UUID SUBMISSION_101 = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private JudgeService judgeService;

    private final ExecutorService directExecutor = new DirectExecutorService();

    @AfterEach
    void tearDown() {
        directExecutor.shutdown();
    }

    @Test
    void consume_readsSubmissionIdAndDelegatesToJudgeService() {
        Semaphore semaphore = new Semaphore(2);
        JudgeQueueConsumer consumer = new JudgeQueueConsumer(
                redisTemplate,
                judgeService,
                directExecutor,
                semaphore,
                "judge:queue"
        );

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPop("judge:queue")).thenReturn(SUBMISSION_101.toString());

        consumer.consume();

        verify(judgeService).judge(SUBMISSION_101);
        assertThat(semaphore.availablePermits()).isEqualTo(2);
    }

    @Test
    void consume_skipsInvalidPayloadAndReleasesSemaphore() {
        Semaphore semaphore = new Semaphore(2);
        JudgeQueueConsumer consumer = new JudgeQueueConsumer(
                redisTemplate,
                judgeService,
                directExecutor,
                semaphore,
                "judge:queue"
        );

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPop("judge:queue")).thenReturn("not-a-number");

        consumer.consume();

        verify(judgeService, never()).judge(org.mockito.ArgumentMatchers.any(UUID.class));
        assertThat(semaphore.availablePermits()).isEqualTo(2);
    }

    @Test
    void consume_handlesRedisConnectionFailureAndReleasesSemaphore() {
        Semaphore semaphore = new Semaphore(2);
        JudgeQueueConsumer consumer = new JudgeQueueConsumer(
                redisTemplate,
                judgeService,
                directExecutor,
                semaphore,
                "judge:queue"
        );

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPop("judge:queue")).thenThrow(new RedisConnectionFailureException("redis down"));

        consumer.consume();

        verify(judgeService, never()).judge(org.mockito.ArgumentMatchers.any(UUID.class));
        assertThat(semaphore.availablePermits()).isEqualTo(2);
    }

    @Test
    void consume_doesNotPollRedisWhenMaxConcurrencyIsReached() {
        Semaphore semaphore = new Semaphore(0);
        JudgeQueueConsumer consumer = new JudgeQueueConsumer(
                redisTemplate,
                judgeService,
                mock(ExecutorService.class),
                semaphore,
                "judge:queue"
        );

        consumer.consume();

        verify(redisTemplate, never()).opsForList();
        verify(judgeService, never()).judge(org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void consume_processesAtMostTwoSubmissionsConcurrently() throws InterruptedException {
        Semaphore semaphore = new Semaphore(2);
        ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(2);
        JudgeQueueConsumer consumer = new JudgeQueueConsumer(
                redisTemplate,
                judgeService,
                executorService,
                semaphore,
                "judge:queue"
        );
        AtomicInteger runningCount = new AtomicInteger();
        AtomicInteger maxRunningCount = new AtomicInteger();
        CountDownLatch firstTwoStarted = new CountDownLatch(2);
        CountDownLatch releaseWorkers = new CountDownLatch(1);

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPop("judge:queue")).thenReturn(
                "00000000-0000-0000-0000-000000000201",
                "00000000-0000-0000-0000-000000000202"
        );

        org.mockito.Mockito.doAnswer(invocation -> {
            int running = runningCount.incrementAndGet();
            maxRunningCount.accumulateAndGet(running, Math::max);
            firstTwoStarted.countDown();
            releaseWorkers.await(2, TimeUnit.SECONDS);
            runningCount.decrementAndGet();
            return null;
        }).when(judgeService).judge(org.mockito.ArgumentMatchers.any(UUID.class));

        consumer.consume();
        consumer.consume();

        assertThat(firstTwoStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(semaphore.availablePermits()).isZero();

        consumer.consume();

        verify(listOperations, org.mockito.Mockito.times(2)).leftPop("judge:queue");
        assertThat(maxRunningCount.get()).isEqualTo(2);

        releaseWorkers.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        assertThat(semaphore.availablePermits()).isEqualTo(2);
    }

    static class DirectExecutorService implements ExecutorService {

        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public <T> Future<T> submit(java.util.concurrent.Callable<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            task.run();
            return java.util.concurrent.CompletableFuture.completedFuture(result);
        }

        @Override
        public Future<?> submit(Runnable task) {
            task.run();
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends java.util.concurrent.Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(Collection<? extends java.util.concurrent.Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
