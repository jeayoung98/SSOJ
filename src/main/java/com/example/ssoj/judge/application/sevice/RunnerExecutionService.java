package com.example.ssoj.judge.application.sevice;

import com.example.ssoj.judge.application.selector.RunnerLanguageExecutorSelector;
import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.executor.LanguageExecutor;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "worker.role", havingValue = "runner")
public class RunnerExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RunnerExecutionService.class);

    private final RunnerLanguageExecutorSelector runnerLanguageExecutorSelector;

    public RunnerExecutionService(RunnerLanguageExecutorSelector runnerLanguageExecutorSelector) {
        this.runnerLanguageExecutorSelector = runnerLanguageExecutorSelector;
    }

    public RunnerExecutionResponse execute(RunnerExecutionRequest request) {
        LanguageExecutor executor = runnerLanguageExecutorSelector.find(request.language());
        if (executor == null) {
            return RunnerExecutionResponse.systemError("Unsupported language: " + request.language());
        }

        JudgeContext context = new JudgeContext(
                request.submissionId(),
                request.problemId(),
                request.language(),
                request.sourceCode(),
                request.input(),
                request.timeLimitMs(),
                request.memoryLimitMb()
        );

        try {
            return RunnerExecutionResponse.from(executor.execute(context));
        } catch (Exception exception) {
            log.error(
                    "Runner execution failed for submission {} and language {}",
                    request.submissionId(),
                    request.language(),
                    exception
            );
            return RunnerExecutionResponse.systemError(exception.getMessage());
        }
    }
}
