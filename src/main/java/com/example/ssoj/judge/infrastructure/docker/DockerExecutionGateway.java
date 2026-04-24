package com.example.ssoj.judge.infrastructure.docker;

import com.example.ssoj.judge.application.port.ExecutionGateway;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.executor.LanguageExecutor;
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
    public JudgeRunResult executeSubmission(JudgeRunContext context) {
        LanguageExecutor executor = findExecutor(context.language());
        if (executor == null) {
            return JudgeRunResult.systemError();
        }

        return executor.executeSubmission(context);
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
