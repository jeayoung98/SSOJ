package com.example.ssoj.judge.application.port;

import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;

public interface ExecutionGateway {

    boolean supports(String language);

    JudgeExecutionResult execute(JudgeContext context);
}
