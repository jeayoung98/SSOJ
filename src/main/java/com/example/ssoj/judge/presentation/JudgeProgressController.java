package com.example.ssoj.judge.presentation;

import com.example.ssoj.judge.application.sevice.SubmissionProgressHub;
import com.example.ssoj.judge.domain.model.JudgeProgressEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnProperty(name = "worker.role", havingValue = "orchestrator", matchIfMissing = true)
@RestController
@RequestMapping("/internal/judge-progress")
public class JudgeProgressController {

    private final SubmissionProgressHub submissionProgressHub;

    public JudgeProgressController(SubmissionProgressHub submissionProgressHub) {
        this.submissionProgressHub = submissionProgressHub;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    void publish(@RequestBody JudgeProgressEvent event) {
        // TODO: Validate an internal shared secret if this endpoint is exposed beyond trusted infrastructure.
        submissionProgressHub.publish(event);
    }
}
