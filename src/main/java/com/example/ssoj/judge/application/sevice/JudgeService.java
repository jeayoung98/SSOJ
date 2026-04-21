package com.example.ssoj.judge.application.sevice;

import com.example.ssoj.submission.domain.SubmissionResult;
import com.example.ssoj.judge.application.port.ExecutionGateway;
import com.example.ssoj.judge.domain.model.CaseJudgeResult;
import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.domain.model.StartedJudging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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

    public void judge(UUID submissionId) {
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
            return new JudgeRunResult(List.of(), SubmissionResult.AC, null, null);
        }

        List<CaseJudgeResult> caseResults = new java.util.ArrayList<>();
        SubmissionResult finalResult = SubmissionResult.AC;
        Integer maxExecutionTimeMs = null;
        Integer maxMemoryKb = null;
        Integer failedTestcaseOrder = null;

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
            SubmissionResult caseResult = determineCaseResult(
                    startedJudging.language(),
                    executionResult,
                    testCase.expectedOutput(),
                    startedJudging.memoryLimitMb()
            );
            maxExecutionTimeMs = max(maxExecutionTimeMs, executionResult.executionTimeMs());
            maxMemoryKb = max(maxMemoryKb, executionResult.memoryUsageKb());

            caseResults.add(new CaseJudgeResult(
                    testCase.testCaseId(),
                    testCase.testCaseOrder(),
                    caseResult,
                    executionResult.executionTimeMs(),
                    executionResult.memoryUsageKb(),
                    executionResult.stderr()
            ));

            if (caseResult != SubmissionResult.AC) {
                finalResult = caseResult;
                failedTestcaseOrder = hasFailedTestcaseOrder(caseResult) ? testCase.testCaseOrder() : null;
                break;
            }
        }

        return new JudgeRunResult(caseResults, finalResult, maxExecutionTimeMs, maxMemoryKb, failedTestcaseOrder);
    }

    private SubmissionResult determineCaseResult(
            String language,
            JudgeExecutionResult executionResult,
            String expectedOutput,
            Integer memoryLimitMb
    ) {
        if (executionResult.systemError()) {
            return SubmissionResult.SYSTEM_ERROR;
        }

        if (executionResult.timedOut()) {
            return SubmissionResult.TLE;
        }

        if (isMemoryLimitExceeded(executionResult, memoryLimitMb)) {
            return SubmissionResult.MLE;
        }

        if (!executionResult.success()) {
            if ("java".equalsIgnoreCase(language)
                    && executionResult.stderr() != null
                    && executionResult.stderr().contains("error:")) {
                return SubmissionResult.CE;
            }

            return SubmissionResult.RE;
        }

        if (!matchesExpectedOutput(executionResult.stdout(), expectedOutput)) {
            return SubmissionResult.WA;
        }

        return SubmissionResult.AC;
    }

    private boolean hasFailedTestcaseOrder(SubmissionResult result) {
        return result == SubmissionResult.WA
                || result == SubmissionResult.TLE
                || result == SubmissionResult.RE
                || result == SubmissionResult.MLE;
    }

    private boolean isMemoryLimitExceeded(JudgeExecutionResult executionResult, Integer memoryLimitMb) {
        if (memoryLimitMb == null) {
            return false;
        }

        if (executionResult.memoryUsageKb() != null) {
            return executionResult.memoryUsageKb() > memoryLimitMb * 1024;
        }

        return Integer.valueOf(137).equals(executionResult.exitCode());
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

    private Integer max(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return Math.max(current, candidate);
    }
}
