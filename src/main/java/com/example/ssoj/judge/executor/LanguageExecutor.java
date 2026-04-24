package com.example.ssoj.judge.executor;

import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;

public interface LanguageExecutor {

    boolean supports(String language);

    JudgeRunResult executeSubmission(JudgeRunContext context);
}
