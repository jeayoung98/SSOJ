package com.example.ssoj.judge.application.port;

import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;

public interface RemoteExecutionClient {

    RunnerExecutionResponse execute(RunnerExecutionRequest request);
}
