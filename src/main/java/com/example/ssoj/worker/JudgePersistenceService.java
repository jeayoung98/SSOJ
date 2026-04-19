package com.example.ssoj.worker;

import com.example.ssoj.submission.Submission;
import com.example.ssoj.submission.SubmissionCaseResult;
import com.example.ssoj.submission.SubmissionCaseResultRepository;
import com.example.ssoj.submission.SubmissionRepository;
import com.example.ssoj.submission.SubmissionStatus;
import com.example.ssoj.testcase.TestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@ConditionalOnProperty(name = "worker.role", havingValue = "orchestrator", matchIfMissing = true)
public class JudgePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(JudgePersistenceService.class);

    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionCaseResultRepository submissionCaseResultRepository;

    public JudgePersistenceService(
            SubmissionRepository submissionRepository,
            TestCaseRepository testCaseRepository,
            SubmissionCaseResultRepository submissionCaseResultRepository
    ) {
        this.submissionRepository = submissionRepository;
        this.testCaseRepository = testCaseRepository;
        this.submissionCaseResultRepository = submissionCaseResultRepository;
    }

    @Transactional
    public StartedJudging startJudging(Long submissionId) {
        Submission submission = submissionRepository.findByIdForUpdate(submissionId).orElse(null);
        if (submission == null) {
            log.warn("Submission {} does not exist", submissionId);
            return null;
        }

        if (submission.isCompleted()) {
            log.info("Submission {} is already completed with status={}", submissionId, submission.getStatus());
            return null;
        }

        boolean updated = submission.markAsJudging(Instant.now());
        if (!updated) {
            log.info("Submission {} is ignored because current status={}", submissionId, submission.getStatus());
            return null;
        }

        List<HiddenTestCaseSnapshot> hiddenTestCases = testCaseRepository
                .findAllByProblem_IdAndHiddenTrueOrderByIdAsc(submission.getProblem().getId())
                .stream()
                .map(testCase -> new HiddenTestCaseSnapshot(
                        testCase.getId(),
                        testCase.getInput(),
                        testCase.getOutput()
                ))
                .toList();

        log.info("Submission {} changed from PENDING to JUDGING", submissionId);

        return new StartedJudging(
                submission.getId(),
                submission.getProblem().getId(),
                submission.getLanguage(),
                submission.getSourceCode(),
                submission.getProblem().getTimeLimitMs(),
                submission.getProblem().getMemoryLimitMb(),
                hiddenTestCases
        );
    }

    @Transactional
    public void saveResultsAndFinish(Long submissionId, JudgeRunResult runResult, Instant finishedAt) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            log.warn("Submission {} disappeared before saving judge result", submissionId);
            return;
        }

        for (CaseJudgeResult caseResult : runResult.caseResults()) {
            submissionCaseResultRepository.save(new SubmissionCaseResult(
                    submission,
                    testCaseRepository.getReferenceById(caseResult.testCaseId()),
                    caseResult.status(),
                    caseResult.executionTimeMs(),
                    caseResult.memoryUsageKb()
            ));
        }

        submission.finish(runResult.finalStatus(), finishedAt);
        log.info("Submission {} finished with status={}", submissionId, runResult.finalStatus());
    }
}
