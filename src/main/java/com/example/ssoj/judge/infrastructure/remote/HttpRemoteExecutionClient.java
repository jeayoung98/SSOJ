package com.example.ssoj.judge.infrastructure.remote;

import com.example.ssoj.judge.application.port.RemoteExecutionClient;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@ConditionalOnProperty(name = "judge.execution.mode", havingValue = "remote")
public class HttpRemoteExecutionClient implements RemoteExecutionClient {

    private final RestTemplate restTemplate;
    private final String executeUrl;

    @Autowired
    public HttpRemoteExecutionClient(
            @Value("${judge.execution.remote.base-url}") String baseUrl,
            @Value("${judge.execution.remote.execute-path:/internal/runner-executions}") String executePath,
            @Value("${judge.execution.remote.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${judge.execution.remote.read-timeout-ms:30000}") long readTimeoutMs
    ) {
        this(
                restTemplate(connectTimeoutMs, readTimeoutMs),
                joinUrl(baseUrl, executePath)
        );
    }

    public HttpRemoteExecutionClient(RestTemplate restTemplate, String executeUrl) {
        this.restTemplate = restTemplate;
        this.executeUrl = executeUrl;
    }

    @Override
    public RunnerExecutionResponse execute(RunnerExecutionRequest request) {
        try {
            ResponseEntity<RunnerExecutionResponse> response = restTemplate.postForEntity(
                    executeUrl,
                    request,
                    RunnerExecutionResponse.class
            );
            return response.getBody();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Remote execution request failed", exception);
        }
    }

    private static String joinUrl(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBaseUrl + normalizedPath;
    }

    private static RestTemplate restTemplate(long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) connectTimeoutMs);
        requestFactory.setReadTimeout((int) readTimeoutMs);
        return new RestTemplate(requestFactory);
    }
}
