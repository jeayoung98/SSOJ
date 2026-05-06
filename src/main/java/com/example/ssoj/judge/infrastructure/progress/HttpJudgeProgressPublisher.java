package com.example.ssoj.judge.infrastructure.progress;

import com.example.ssoj.judge.application.port.JudgeProgressPublisher;
import com.example.ssoj.judge.domain.model.JudgeProgressEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "worker.executor.progress.enabled", havingValue = "true")
public class HttpJudgeProgressPublisher implements JudgeProgressPublisher {

    private static final Logger log = LoggerFactory.getLogger(HttpJudgeProgressPublisher.class);

    private final String callbackUrl;
    private final RestClient restClient;
    private final ExecutorService executorService;

    public HttpJudgeProgressPublisher(
            @Value("${worker.executor.progress.callback-url:}") String callbackUrl,
            @Value("${worker.executor.progress.callback-timeout-ms:500}") long callbackTimeoutMs,
            @Value("${worker.executor.progress.async-threads:2}") int asyncThreads
    ) {
        this.callbackUrl = callbackUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMs = Math.toIntExact(Math.max(1L, callbackTimeoutMs));
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.executorService = Executors.newFixedThreadPool(Math.max(1, asyncThreads));
    }

    @Override
    public void publish(JudgeProgressEvent event) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            log.warn("Progress callback-url is blank. submissionId={}", event.submissionId());
            return;
        }

        try {
            executorService.submit(() -> postProgress(event));
        } catch (Exception exception) {
            log.warn("Progress publish failed submissionId={}", event.submissionId(), exception);
        }
    }

    private void postProgress(JudgeProgressEvent event) {
        try {
            restClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            log.warn("Progress publish failed submissionId={}", event.submissionId(), exception);
        }
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
}
