package com.example.ssoj.worker;

import com.example.ssoj.submission.Submission;
import com.example.ssoj.submission.SubmissionCaseResult;
import com.example.ssoj.submission.SubmissionCaseResultRepository;
import com.example.ssoj.submission.SubmissionRepository;
import com.example.ssoj.submission.SubmissionStatus;
import com.example.ssoj.testcase.TestCase;
import com.example.ssoj.testcase.TestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);
    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionCaseResultRepository submissionCaseResultRepository;
    private final List<LanguageExecutor> languageExecutors;

    public JudgeService(
            SubmissionRepository submissionRepository,
            TestCaseRepository testCaseRepository,
            SubmissionCaseResultRepository submissionCaseResultRepository,
            List<LanguageExecutor> languageExecutors
    ) {
        this.submissionRepository = submissionRepository;
        this.testCaseRepository = testCaseRepository;
        this.submissionCaseResultRepository = submissionCaseResultRepository;
        this.languageExecutors = languageExecutors;
    }

    @Transactional
    public void judge(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            log.warn("Submission {} does not exist", submissionId);
            return;
        }

        if (submission.isCompleted()) {
            log.info("Submission {} is already completed with status={}", submissionId, submission.getStatus());
            return;
        }

        boolean updated = submission.markAsJudging(Instant.now());
        if (!updated) {
            log.info("Submission {} is ignored because current status={}", submissionId, submission.getStatus());
            return;
        }

        log.info("Submission {} changed from PENDING to JUDGING", submissionId);

        SubmissionStatus finalStatus = SubmissionStatus.SYSTEM_ERROR;
        try {
            LanguageExecutor executor = findExecutor(submission.getLanguage());
            if (executor == null) {
                log.warn("No LanguageExecutor found for language={}", submission.getLanguage());
            } else {
                List<TestCase> hiddenTestCases = testCaseRepository.findAllByProblem_IdAndHiddenTrueOrderByIdAsc(
                        submission.getProblem().getId()
                );

                finalStatus = SubmissionStatus.AC;
                for (TestCase testCase : hiddenTestCases) {
                    JudgeContext context = new JudgeContext(
                            submission.getId(),
                            submission.getProblem().getId(),
                            submission.getLanguage(),
                            submission.getSourceCode(),
                            testCase.getInput(),
                            submission.getProblem().getTimeLimitMs(),
                            submission.getProblem().getMemoryLimitMb()
                    );

                    JudgeExecutionResult executionResult = executor.execute(context);
                    SubmissionStatus caseStatus = determineCaseStatus(submission.getLanguage(), executionResult, testCase);

                    submissionCaseResultRepository.save(new SubmissionCaseResult(
                            submission,
                            testCase,
                            caseStatus,
                            executionResult.executionTimeMs(),
                            executionResult.memoryUsageKb()
                    ));

                    if (finalStatus == SubmissionStatus.AC && caseStatus != SubmissionStatus.AC) {
                        finalStatus = caseStatus;
                    }
                }
            }
        } catch (Exception exception) {
            log.error("JudgeService failed while processing submission {}", submissionId, exception);
            finalStatus = SubmissionStatus.SYSTEM_ERROR;
        } finally {
            submission.finish(finalStatus, Instant.now());
        }

        log.info("Submission {} finished with status={}", submissionId, finalStatus);
    }

    private LanguageExecutor findExecutor(String language) {
        for (LanguageExecutor languageExecutor : languageExecutors) {
            if (languageExecutor.supports(language)) {
                return languageExecutor;
            }
        }

        return null;
    }

    private SubmissionStatus determineCaseStatus(String language, JudgeExecutionResult executionResult, TestCase testCase) {
        if (executionResult.systemError()) {
            return SubmissionStatus.SYSTEM_ERROR;
        }

        if (executionResult.timedOut()) {
            return SubmissionStatus.TLE;
        }

        if (!executionResult.success()) {
            if ("java".equalsIgnoreCase(language) && executionResult.stderr() != null && executionResult.stderr().contains("error:")) {
                return SubmissionStatus.CE;
            }

            return SubmissionStatus.RE;
        }

        if (!matchesExpectedOutput(executionResult.stdout(), testCase.getOutput())) {
            return SubmissionStatus.WA;
        }

        return SubmissionStatus.AC;
    }

    private boolean matchesExpectedOutput(String actualOutput, String expectedOutput) {
        List<String> actualLines = normalizeLines(actualOutput);
        List<String> expectedLines = normalizeLines(expectedOutput);
        return actualLines.equals(expectedLines);
    }

    private List<String> normalizeLines(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        return Arrays.stream(output.trim().split("\\R"))
                .map(String::trim)
                .toList();
    }
}
