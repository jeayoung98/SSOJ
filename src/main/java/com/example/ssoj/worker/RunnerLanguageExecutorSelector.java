package com.example.ssoj.worker;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
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
