package com.example.ssoj.judge.application.sevice;

import com.example.ssoj.judge.infrastructure.config.RunnerExecutionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "worker.role", havingValue = "runner")
@EnableConfigurationProperties(RunnerExecutionProperties.class)
public class RunnerExecutionLimiter {

    private static final Logger log = LoggerFactory.getLogger(RunnerExecutionLimiter.class);
    private static final long ACQUIRE_TIMEOUT_SECONDS = 3;

    private final Semaphore semaphore;

    public RunnerExecutionLimiter(RunnerExecutionProperties properties) {
        this.semaphore = new Semaphore(properties.maxConcurrentExecutions(), true);
    }

    public <T> T executeWithLimit(Long submissionId, Supplier<T> execution) {
        boolean acquired = false;
        try {
            log.info(
                    "Runner execution waiting for slot submissionId={} availablePermits={}",
                    submissionId,
                    semaphore.availablePermits()
            );
            acquired = semaphore.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new RunnerBusyException("Runner is busy. Please retry later.");
            }
            log.info(
                    "Runner execution slot acquired submissionId={} availablePermits={}",
                    submissionId,
                    semaphore.availablePermits()
            );
            return execution.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RunnerBusyException("Interrupted while waiting for runner execution slot.", exception);
        } finally {
            if (acquired) {
                semaphore.release();
                log.info(
                        "Runner execution slot released submissionId={} availablePermits={}",
                        submissionId,
                        semaphore.availablePermits()
                );
            }
        }
    }
}
