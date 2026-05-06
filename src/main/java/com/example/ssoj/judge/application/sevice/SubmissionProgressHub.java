package com.example.ssoj.judge.application.sevice;

import com.example.ssoj.judge.domain.model.JudgeProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@ConditionalOnProperty(name = "worker.role", havingValue = "orchestrator", matchIfMissing = true)
public class SubmissionProgressHub {

    private static final Logger log = LoggerFactory.getLogger(SubmissionProgressHub.class);
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long submissionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.computeIfAbsent(submissionId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onTimeout(() -> remove(submissionId, emitter));
        emitter.onCompletion(() -> remove(submissionId, emitter));
        emitter.onError(throwable -> remove(submissionId, emitter));

        log.info("SSE connected submissionId={}", submissionId);
        return emitter;
    }

    public void publish(JudgeProgressEvent event) {
        List<SseEmitter> submissionEmitters = emitters.get(event.submissionId());
        if (submissionEmitters == null || submissionEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : submissionEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.phase())
                        .data(event));
            } catch (IOException | IllegalStateException exception) {
                log.warn("SSE send failed submissionId={}", event.submissionId(), exception);
                remove(event.submissionId(), emitter);
            }
        }

        log.debug(
                "Progress emitted submissionId={} phase={} completedTestcases={} totalTestcases={}",
                event.submissionId(),
                event.phase(),
                event.completedTestcases(),
                event.totalTestcases()
        );
    }

    public void complete(Long submissionId) {
        List<SseEmitter> submissionEmitters = emitters.remove(submissionId);
        if (submissionEmitters == null) {
            return;
        }

        for (SseEmitter emitter : submissionEmitters) {
            emitter.complete();
        }
    }

    public void remove(Long submissionId, SseEmitter emitter) {
        List<SseEmitter> submissionEmitters = emitters.get(submissionId);
        if (submissionEmitters == null) {
            return;
        }

        submissionEmitters.remove(emitter);
        if (submissionEmitters.isEmpty()) {
            emitters.remove(submissionId, submissionEmitters);
        }
    }

    int subscriberCount(Long submissionId) {
        List<SseEmitter> submissionEmitters = emitters.get(submissionId);
        return submissionEmitters == null ? 0 : submissionEmitters.size();
    }
}
