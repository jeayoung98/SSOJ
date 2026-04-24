package com.example.ssoj.judge.presentation;

import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;
import com.example.ssoj.judge.application.sevice.RunnerExecutionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnProperty(name = "worker.role", havingValue = "runner")
@RestController
@RequestMapping("/internal/runner-executions")
public class RunnerExecutionController {

    private final RunnerExecutionService runnerExecutionService;

    public RunnerExecutionController(RunnerExecutionService runnerExecutionService) {
        this.runnerExecutionService = runnerExecutionService;
    }

    @PostMapping
    RunnerExecutionResponse execute(@RequestBody RunnerExecutionRequest request) {
        return runnerExecutionService.executeSubmission(request);
    }
}
