package com.example.ssoj.judge.application.selector;

import com.example.ssoj.judge.executor.LanguageExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "worker.role", havingValue = "runner")
public class RunnerLanguageExecutorSelector {

    private final List<LanguageExecutor> languageExecutors;

    public RunnerLanguageExecutorSelector(List<LanguageExecutor> languageExecutors) {
        this.languageExecutors = languageExecutors;
    }

    public LanguageExecutor find(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }

        for (LanguageExecutor languageExecutor : languageExecutors) {
            if (languageExecutor.supports(language)) {
                return languageExecutor;
            }
        }

        return null;
    }
}
