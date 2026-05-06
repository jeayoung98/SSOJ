package com.example.ssoj.judge.application.sevice;

import com.example.ssoj.judge.domain.model.JudgeProgressEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionProgressHubTest {

    @Test
    void subscribeAndRemove_managesEmitterList() {
        SubmissionProgressHub hub = new SubmissionProgressHub();

        SseEmitter emitter = hub.subscribe(220L);

        assertThat(hub.subscriberCount(220L)).isEqualTo(1);

        hub.remove(220L, emitter);

        assertThat(hub.subscriberCount(220L)).isZero();
    }

    @Test
    void publish_withoutSubscribersDoesNotFail() {
        SubmissionProgressHub hub = new SubmissionProgressHub();

        hub.publish(JudgeProgressEvent.running(220L, 37, 100, 37));

        assertThat(hub.subscriberCount(220L)).isZero();
    }
}
