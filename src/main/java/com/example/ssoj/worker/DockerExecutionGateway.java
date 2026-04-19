package com.example.ssoj.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "judge.execution.mode", havingValue = "docker", matchIfMissing = true)
public class DockerExecutionGateway implements ExecutionGateway {

    private final List<LanguageExecutor> languageExecutors;

    public DockerExecutionGateway(List<LanguageExecutor> languageExecutors) {
        this.languageExecutors = languageExecutors;
    }

    @Override
    public boolean supports(String language) {
        return findExecutor(language) != null;
    }

    @Override
    public JudgeExecutionResult execute(JudgeContext context) {
        LanguageExecutor executor = findExecutor(context.language());
        if (executor == null) {
            return JudgeExecutionResult.systemError("Unsupported language: " + context.language());
        }

        return executor.execute(context);
    }

    private LanguageExecutor findExecutor(String language) {
        for (LanguageExecutor languageExecutor : languageExecutors) {
            if (languageExecutor.supports(language)) {
                return languageExecutor;
            }
        }

        return null;
    }
}
