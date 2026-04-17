package com.example.ssoj.worker;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeQueueConsumerTest {

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
        // 비동기성을 제거한 직접 실행 executor로 consume 흐름만 검증한다.
        Semaphore semaphore = new Semaphore(2);
        JudgeQueueConsumer consumer = new JudgeQueueConsumer(redisTemplate, judgeService, directExecutor, semaphore);

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPop("judge:queue")).thenReturn("101");

        consumer.consume();

        verify(judgeService).judge(101L);
        assertThat(semaphore.availablePermits()).isEqualTo(2);
    }

    @Test
    void consume_skipsInvalidPayloadAndReleasesSemaphore() {
        // 잘못된 payload를 읽어도 permit이 회수되지 않으면 이후 consume이 막힌다.
        Semaphore semaphore = new Semaphore(2);
        JudgeQueueConsumer consumer = new JudgeQueueConsumer(redisTemplate, judgeService, directExecutor, semaphore);

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPop("judge:queue")).thenReturn("not-a-number");

        consumer.consume();

        verify(judgeService, never()).judge(org.mockito.ArgumentMatchers.anyLong());
        assertThat(semaphore.availablePermits()).isEqualTo(2);
    }

    @Test
    void consume_handlesRedisConnectionFailureAndReleasesSemaphore() {
        // Redis 장애 시에도 permit 누수가 없어야 한다.
        Semaphore semaphore = new Semaphore(2);
        JudgeQueueConsumer consumer = new JudgeQueueConsumer(redisTemplate, judgeService, directExecutor, semaphore);

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPop("judge:queue")).thenThrow(new RedisConnectionFailureException("redis down"));

        consumer.consume();

        verify(judgeService, never()).judge(org.mockito.ArgumentMatchers.anyLong());
        assertThat(semaphore.availablePermits()).isEqualTo(2);
    }

    @Test
    void consume_doesNotPollRedisWhenMaxConcurrencyIsReached() {
        // 동시성 한도를 넘긴 상태에서는 큐를 더 읽지 않아야 한다.
        Semaphore semaphore = new Semaphore(0);
        JudgeQueueConsumer consumer = new JudgeQueueConsumer(redisTemplate, judgeService, mock(ExecutorService.class), semaphore);

        consumer.consume();

        verify(redisTemplate, never()).opsForList();
        verify(judgeService, never()).judge(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void consume_processesAtMostTwoSubmissionsConcurrently() throws InterruptedException {
        // 실제 스레드 풀을 써서 동시에 2개까지만 실행되는지 확인한다.
        Semaphore semaphore = new Semaphore(2);
        ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(2);
        JudgeQueueConsumer consumer = new JudgeQueueConsumer(redisTemplate, judgeService, executorService, semaphore);
        AtomicInteger runningCount = new AtomicInteger();
        AtomicInteger maxRunningCount = new AtomicInteger();
        CountDownLatch firstTwoStarted = new CountDownLatch(2);
        CountDownLatch releaseWorkers = new CountDownLatch(1);

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPop("judge:queue")).thenReturn("201", "202");

        org.mockito.Mockito.doAnswer(invocation -> {
            // 두 작업이 동시에 들어온 시점을 잡아 최대 병렬 수를 기록한다.
            int running = runningCount.incrementAndGet();
            maxRunningCount.accumulateAndGet(running, Math::max);
            firstTwoStarted.countDown();
            releaseWorkers.await(2, TimeUnit.SECONDS);
            runningCount.decrementAndGet();
            return null;
        }).when(judgeService).judge(org.mockito.ArgumentMatchers.anyLong());

        consumer.consume();
        consumer.consume();

        // 앞의 두 작업이 시작될 때까지 기다린 뒤 세 번째 consume이 큐를 읽지 않는지 본다.
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
        // submit 즉시 현재 스레드에서 실행해 비동기 타이밍 영향을 제거한다.

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
