package com.example.ssoj.judge.application.port;

import com.example.ssoj.judge.domain.model.JudgeProgressEvent;

public interface JudgeProgressPublisher {

    void publish(JudgeProgressEvent event);
}
