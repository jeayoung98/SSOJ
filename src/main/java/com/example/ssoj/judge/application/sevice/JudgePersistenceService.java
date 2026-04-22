package com.example.ssoj.judge.application.sevice;

import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.domain.model.StartedJudging;
import com.example.ssoj.submission.domain.Submission;
import com.example.ssoj.submission.infrastructure.SubmissionRepository;
import com.example.ssoj.testcase.infrastructure.ProblemTestcaseRepository;
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
    private final ProblemTestcaseRepository problemTestcaseRepository;

    public JudgePersistenceService(
            SubmissionRepository submissionRepository,
            ProblemTestcaseRepository problemTestcaseRepository
    ) {
        this.submissionRepository = submissionRepository;
        this.problemTestcaseRepository = problemTestcaseRepository;
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

        boolean updated = submission.markAsJudging();
        if (!updated) {
            log.info("Submission {} is ignored because current status={}", submissionId, submission.getStatus());
            return null;
        }

        List<HiddenTestCaseSnapshot> hiddenTestCases = problemTestcaseRepository
                .findAllByProblem_IdAndHiddenTrueOrderByTestcaseOrderAsc(submission.getProblem().getId())
                .stream()
                .map(testcase -> new HiddenTestCaseSnapshot(
                        testcase.getId(),
                        testcase.getTestcaseOrder(),
                        testcase.getInputText(),
                        testcase.getExpectedOutput()
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
    public void saveResultsAndFinish(Long submissionId, JudgeRunResult runResult, Instant judgedAt) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            log.warn("Submission {} disappeared before saving judge result", submissionId);
            return;
        }

        submission.finish(
                runResult.finalResult(),
                runResult.executionTimeMs(),
                runResult.memoryKb(),
                runResult.failedTestcaseOrder(),
                judgedAt
        );
        log.info("Submission {} finished with result={}", submissionId, runResult.finalResult());
    }
}
