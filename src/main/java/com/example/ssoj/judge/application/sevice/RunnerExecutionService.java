package com.example.ssoj.judge.application.sevice;

import com.example.ssoj.judge.application.selector.RunnerLanguageExecutorSelector;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.executor.LanguageExecutor;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "worker.role", havingValue = "runner")
public class RunnerExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RunnerExecutionService.class);

    private final RunnerLanguageExecutorSelector runnerLanguageExecutorSelector;
    private final RunnerExecutionLimiter runnerExecutionLimiter;

    public RunnerExecutionService(
            RunnerLanguageExecutorSelector runnerLanguageExecutorSelector,
            RunnerExecutionLimiter runnerExecutionLimiter
    ) {
        this.runnerLanguageExecutorSelector = runnerLanguageExecutorSelector;
        this.runnerExecutionLimiter = runnerExecutionLimiter;
    }

    public RunnerExecutionResponse executeSubmission(RunnerExecutionRequest request) {
        LanguageExecutor executor = runnerLanguageExecutorSelector.find(request.language());
        if (executor == null) {
            return RunnerExecutionResponse.systemError();
        }

        JudgeRunContext context = new JudgeRunContext(
                request.submissionId(),
                request.problemId(),
                request.language(),
                request.sourceCode(),
                request.toHiddenTestCases(),
                request.timeLimitMs(),
                request.memoryLimitMb()
        );

        return runnerExecutionLimiter.executeWithLimit(request.submissionId(), () -> executeWithExecutor(request, executor, context));
    }

    private RunnerExecutionResponse executeWithExecutor(
            RunnerExecutionRequest request,
            LanguageExecutor executor,
            JudgeRunContext context
    ) {
        try {
            JudgeRunResult result = executor.executeSubmission(context);
            JudgeRunResult normalizedResult = preventExecutedResultWithoutMetrics(request, result);
            return RunnerExecutionResponse.from(request.submissionId(), normalizedResult);
        } catch (Exception exception) {
            log.error(
                    "Runner execution failed for submission {} and language {}",
                    request.submissionId(),
                    request.language(),
                    exception
            );
            return RunnerExecutionResponse.systemError();
        }
    }

    private JudgeRunResult preventExecutedResultWithoutMetrics(
            RunnerExecutionRequest request,
            JudgeRunResult result
    ) {
        if (!requiresExecutionMetrics(result.finalResult()) || request.toHiddenTestCases().isEmpty()) {
            return result;
        }

        if (result.executionTimeMs() != null && result.memoryKb() != null) {
            return result;
        }

        log.error(
                "Runner execution result is missing metrics after user code execution. submissionId={} result={} executionTimeMs={} memoryKb={} failedTestcaseOrder={}",
                request.submissionId(),
                result.finalResult(),
                result.executionTimeMs(),
                result.memoryKb(),
                result.failedTestcaseOrder()
        );
        return JudgeRunResult.systemError();
    }

    private boolean requiresExecutionMetrics(SubmissionResult result) {
        return result == SubmissionResult.AC
                || result == SubmissionResult.WA
                || result == SubmissionResult.RE
                || result == SubmissionResult.TLE
                || result == SubmissionResult.MLE;
    }
}
