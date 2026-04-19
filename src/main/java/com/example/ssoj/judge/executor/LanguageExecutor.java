package com.example.ssoj.judge.executor;

import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;

public interface LanguageExecutor {

    boolean supports(String language);

    JudgeExecutionResult execute(JudgeContext context);
}
