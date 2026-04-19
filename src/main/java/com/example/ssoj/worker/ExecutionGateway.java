package com.example.ssoj.worker;

public interface ExecutionGateway {

    boolean supports(String language);

    JudgeExecutionResult execute(JudgeContext context);
}
