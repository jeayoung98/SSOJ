package com.example.ssoj.worker;

import com.example.ssoj.submission.SubmissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
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

        List<HiddenTestCaseSnapshot> hiddenTestCases = startedJudging.hiddenTestCases();
        if (hiddenTestCases.isEmpty()) {
            log.info("Submission {} has no hidden test cases. Finishing with AC.", startedJudging.submissionId());
            return new JudgeRunResult(List.of(), SubmissionStatus.AC);
        }

        List<CaseJudgeResult> caseResults = new java.util.ArrayList<>();
        SubmissionStatus finalStatus = SubmissionStatus.AC;

        for (HiddenTestCaseSnapshot testCase : hiddenTestCases) {
            JudgeContext context = new JudgeContext(
                    startedJudging.submissionId(),
                    startedJudging.problemId(),
                    startedJudging.language(),
                    startedJudging.sourceCode(),
                    testCase.input(),
                    startedJudging.timeLimitMs(),
                    startedJudging.memoryLimitMb()
            );

            JudgeExecutionResult executionResult = executionGateway.execute(context);
            SubmissionStatus caseStatus = determineCaseStatus(startedJudging.language(), executionResult, testCase.expectedOutput());

            caseResults.add(new CaseJudgeResult(
                    testCase.testCaseId(),
                    caseStatus,
                    executionResult.executionTimeMs(),
                    executionResult.memoryUsageKb()
            ));

            if (caseStatus != SubmissionStatus.AC) {
                finalStatus = caseStatus;
                break;
            }
        }

        return new JudgeRunResult(caseResults, finalStatus);
    }

    private SubmissionStatus determineCaseStatus(
            String language,
            JudgeExecutionResult executionResult,
            String expectedOutput
    ) {
        if (executionResult.systemError()) {
            return SubmissionStatus.SYSTEM_ERROR;
        }

        if (executionResult.timedOut()) {
            return SubmissionStatus.TLE;
        }

        if (!executionResult.success()) {
            if ("java".equalsIgnoreCase(language)
                    && executionResult.stderr() != null
                    && executionResult.stderr().contains("error:")) {
                return SubmissionStatus.CE;
            }

            return SubmissionStatus.RE;
        }

        if (!matchesExpectedOutput(executionResult.stdout(), expectedOutput)) {
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
