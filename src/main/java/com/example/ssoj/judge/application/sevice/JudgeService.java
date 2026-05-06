package com.example.ssoj.judge.application.sevice;

import com.example.ssoj.judge.application.port.ExecutionGateway;
import com.example.ssoj.judge.domain.model.JudgeProgressEvent;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.domain.model.StartedJudging;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
@Service
@ConditionalOnProperty(name = "worker.role", havingValue = "orchestrator", matchIfMissing = true)
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private final JudgePersistenceService judgePersistenceService;
    private final ExecutionGateway executionGateway;
    private final SubmissionProgressHub submissionProgressHub;

    public JudgeService(
            JudgePersistenceService judgePersistenceService,
            ExecutionGateway executionGateway
    ) {
        this(judgePersistenceService, executionGateway, null);
    }

    @Autowired
    public JudgeService(
            JudgePersistenceService judgePersistenceService,
            ExecutionGateway executionGateway,
            SubmissionProgressHub submissionProgressHub
    ) {
        this.judgePersistenceService = judgePersistenceService;
        this.executionGateway = executionGateway;
        this.submissionProgressHub = submissionProgressHub;
    }

    public void judge(Long submissionId) {
        StartedJudging startedJudging = judgePersistenceService.startJudging(submissionId);
        if (startedJudging == null) {
            return;
        }

        JudgeRunResult runResult = JudgeRunResult.systemError();
        try {
            runResult = runJudgeLogic(startedJudging);
        } catch (Exception exception) {
            log.error("JudgeService failed while processing submission {}", submissionId, exception);
        } finally {
            judgePersistenceService.saveResultsAndFinish(
                    submissionId,
                    runResult,
                    Instant.now()
            );
            publishDoneProgress(startedJudging, runResult);
        }
    }

    private void publishDoneProgress(StartedJudging startedJudging, JudgeRunResult runResult) {
        if (submissionProgressHub == null) {
            return;
        }

        try {
            submissionProgressHub.publish(JudgeProgressEvent.done(
                    startedJudging.submissionId(),
                    startedJudging.hiddenTestCases().size(),
                    progressResult(runResult)
            ));
            submissionProgressHub.complete(startedJudging.submissionId());
        } catch (Exception exception) {
            log.warn("DONE progress emit failed submissionId={}", startedJudging.submissionId(), exception);
        }
    }

    private String progressResult(JudgeRunResult runResult) {
        if (runResult.finalResult().name().equals("JUDGE_ERROR")) {
            return "SYSTEM_ERROR";
        }
        return runResult.finalResult().name();
    }

    JudgeRunResult runJudgeLogic(StartedJudging startedJudging) {
        if (!executionGateway.supports(startedJudging.language())) {
            log.warn("No ExecutionGateway support found for language={}", startedJudging.language());
            return JudgeRunResult.systemError();
        }

        if (startedJudging.hiddenTestCases().isEmpty()) {
            log.error(
                    "Submission {} cannot be judged because problem has no executable testcases. problemId={} testcaseCount={} hiddenTestcaseCount={} reason={}",
                    startedJudging.submissionId(),
                    startedJudging.problemId(),
                    0,
                    startedJudging.hiddenTestcaseCount(),
                    "NO_EXECUTABLE_TESTCASES"
            );
            return JudgeRunResult.judgeError();
        }

        if (startedJudging.hiddenTestcaseCount() == 0) {
            log.warn(
                    "Submission {} will be judged with public testcases because no hidden testcases exist. problemId={} testcaseCount={} hiddenTestcaseCount={} reason={}",
                    startedJudging.submissionId(),
                    startedJudging.problemId(),
                    startedJudging.hiddenTestCases().size(),
                    startedJudging.hiddenTestcaseCount(),
                    "PUBLIC_TESTCASES_ONLY"
            );
        }

        return executionGateway.executeSubmission(new JudgeRunContext(
                startedJudging.submissionId(),
                startedJudging.problemId(),
                startedJudging.language(),
                startedJudging.sourceCode(),
                startedJudging.hiddenTestCases(),
                startedJudging.timeLimitMs(),
                startedJudging.memoryLimitMb()
        ));
    }
}
