package com.example.ssoj.judge.presentation;

import com.example.ssoj.judge.application.sevice.SubmissionProgressHub;
import com.example.ssoj.judge.domain.model.JudgeProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ConditionalOnProperty(name = "worker.role", havingValue = "orchestrator", matchIfMissing = true)
@RestController
@RequestMapping("/api/submissions")
public class SubmissionProgressController {

    private static final Logger log = LoggerFactory.getLogger(SubmissionProgressController.class);

    private final SubmissionProgressHub submissionProgressHub;

    public SubmissionProgressController(SubmissionProgressHub submissionProgressHub) {
        this.submissionProgressHub = submissionProgressHub;
    }

    @GetMapping(path = "/{submissionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter subscribe(@PathVariable Long submissionId) {
        SseEmitter emitter = submissionProgressHub.subscribe(submissionId);
        try {
            emitter.send(SseEmitter.event()
                    .name("RUNNING")
                    .data(JudgeProgressEvent.running(submissionId, 0, 0, 0)));
        } catch (Exception exception) {
            log.warn("Failed to send initial SSE event submissionId={}", submissionId, exception);
            submissionProgressHub.remove(submissionId, emitter);
        }
        return emitter;
    }
}
