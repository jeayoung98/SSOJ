package com.example.ssoj.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@ConditionalOnProperty(name = "judge.execution.mode", havingValue = "remote")
public class RemoteExecutionGateway implements ExecutionGateway {

    private final RemoteExecutionClient remoteExecutionClient;
    private final List<String> supportedLanguages;

    public RemoteExecutionGateway(
            RemoteExecutionClient remoteExecutionClient,
            @Value("${judge.execution.remote.supported-languages:java,python,cpp}") String supportedLanguages
    ) {
        this.remoteExecutionClient = remoteExecutionClient;
        this.supportedLanguages = Arrays.stream(supportedLanguages.split(","))
                .map(String::trim)
                .filter(language -> !language.isBlank())
                .map(String::toLowerCase)
                .toList();
    }

    @Override
    public boolean supports(String language) {
        return supportedLanguages.contains(language.toLowerCase());
    }

    @Override
    public JudgeExecutionResult execute(JudgeContext context) {
        try {
            RunnerExecutionResponse response = remoteExecutionClient.execute(RunnerExecutionRequest.from(context));
            if (response == null) {
                return JudgeExecutionResult.systemError("Remote runner returned no response");
            }

            return new JudgeExecutionResult(
                    response.success(),
                    response.stdout(),
                    response.stderr(),
                    response.exitCode(),
                    response.executionTimeMs(),
                    response.memoryUsageKb(),
                    response.systemError(),
                    response.timedOut()
            );
        } catch (Exception exception) {
            return JudgeExecutionResult.systemError(exception.getMessage());
        }
    }
}
