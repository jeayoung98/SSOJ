package com.example.ssoj.worker;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/runner-executions")
public class RunnerExecutionController {

    private final RunnerExecutionService runnerExecutionService;

    public RunnerExecutionController(RunnerExecutionService runnerExecutionService) {
        this.runnerExecutionService = runnerExecutionService;
    }

    @PostMapping
    RunnerExecutionResponse execute(@RequestBody RunnerExecutionRequest request) {
        return runnerExecutionService.execute(request);
    }
}
