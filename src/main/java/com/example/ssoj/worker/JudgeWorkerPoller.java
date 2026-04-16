package com.example.ssoj.worker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JudgeWorkerPoller {

    private final JudgeWorkerService judgeWorkerService;

    public JudgeWorkerPoller(JudgeWorkerService judgeWorkerService) {
        this.judgeWorkerService = judgeWorkerService;
    }

    @Scheduled(fixedDelayString = "${worker.poll-delay-ms:1000}")
    public void poll() {
        judgeWorkerService.pollOnce();
    }
}
