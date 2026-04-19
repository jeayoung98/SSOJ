package com.example.ssoj.worker;

public interface RemoteExecutionClient {

    RunnerExecutionResponse execute(RunnerExecutionRequest request);
}
