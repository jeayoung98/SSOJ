package com.example.ssoj.judge.infrastructure.progress;

import com.example.ssoj.judge.application.port.JudgeProgressPublisher;
import com.example.ssoj.judge.domain.model.JudgeProgressEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "worker.executor.progress.enabled", havingValue = "false", matchIfMissing = true)
public class NoopJudgeProgressPublisher implements JudgeProgressPublisher {

    @Override
    public void publish(JudgeProgressEvent event) {
    }
}
