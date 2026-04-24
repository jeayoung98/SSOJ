package com.example.ssoj.judge.application.port;

import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;

public interface ExecutionGateway {

    boolean supports(String language);

    JudgeRunResult executeSubmission(JudgeRunContext context);
}
