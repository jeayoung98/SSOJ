package com.example.ssoj.worker;

public interface LanguageExecutor {

    boolean supports(String language);

    JudgeExecutionResult execute(JudgeContext context);
}
