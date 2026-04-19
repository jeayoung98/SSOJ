package com.example.ssoj.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "judge.dispatch.mode", havingValue = "cloud-tasks")
@ConditionalOnProperty(name = "worker.role", havingValue = "orchestrator", matchIfMissing = true)
public class CloudTasksJudgeDispatchService implements JudgeDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(CloudTasksJudgeDispatchService.class);

    @Override
    public void dispatch(JudgeDispatchCommand command) {
        log.warn(
                "CloudTasksJudgeDispatchService is selected but not implemented yet for submissionId={} requestId={}",
                command.submissionId(),
                command.requestId()
        );
        throw new IllegalStateException("CloudTasksJudgeDispatchService is not implemented yet");
    }
}
