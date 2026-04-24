package com.example.ssoj.judge.application.sevice;

import com.example.ssoj.submission.domain.SubmissionResult;
import com.example.ssoj.judge.application.port.ExecutionGateway;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.domain.model.StartedJudging;
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

    public JudgeService(
            JudgePersistenceService judgePersistenceService,
            ExecutionGateway executionGateway
    ) {
        this.judgePersistenceService = judgePersistenceService;
        this.executionGateway = executionGateway;
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
        }
    }

    JudgeRunResult runJudgeLogic(StartedJudging startedJudging) {
        if (!executionGateway.supports(startedJudging.language())) {
            log.warn("No ExecutionGateway support found for language={}", startedJudging.language());
            return JudgeRunResult.systemError();
        }

        if (startedJudging.hiddenTestCases().isEmpty()) {
            log.info("Submission {} has no hidden test cases. Finishing with AC.", startedJudging.submissionId());
            return new JudgeRunResult(SubmissionResult.AC, null, null);
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
