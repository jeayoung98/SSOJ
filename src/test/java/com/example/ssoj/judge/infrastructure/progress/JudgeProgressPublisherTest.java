package com.example.ssoj.judge.infrastructure.progress;

import com.example.ssoj.judge.domain.model.JudgeProgressEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

class JudgeProgressPublisherTest {

    @Test
    void noopPublisher_ignoresProgress() {
        NoopJudgeProgressPublisher publisher = new NoopJudgeProgressPublisher();

        assertThatNoException().isThrownBy(() -> publisher.publish(JudgeProgressEvent.running(1L, 1, 2, 50)));
    }

    @Test
    void httpPublisher_doesNotThrowWhenCallbackUrlIsBlank() {
        HttpJudgeProgressPublisher publisher = new HttpJudgeProgressPublisher("", 1, 1);
        try {
            assertThatNoException().isThrownBy(() -> publisher.publish(JudgeProgressEvent.running(1L, 1, 2, 50)));
        } finally {
            publisher.shutdown();
        }
    }
}
