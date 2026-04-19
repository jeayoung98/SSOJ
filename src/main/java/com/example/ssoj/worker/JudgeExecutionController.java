package com.example.ssoj.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnProperty(name = "worker.mode", havingValue = "http-trigger")
@RestController
@RequestMapping("/internal/judge-executions")
public class JudgeExecutionController {

    private final JudgeService judgeService;

    public JudgeExecutionController(JudgeService judgeService) {
        this.judgeService = judgeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    void execute(@RequestBody JudgeExecutionCommand command) {
        judgeService.judge(command.submissionId());
    }
}
