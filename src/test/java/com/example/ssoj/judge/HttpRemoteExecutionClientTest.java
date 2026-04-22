package com.example.ssoj.judge;

import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;
import com.example.ssoj.judge.infrastructure.remote.HttpRemoteExecutionClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpRemoteExecutionClientTest {

    @Test
    void execute_postsRunnerExecutionRequestAndReadsRunnerExecutionResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        HttpRemoteExecutionClient client = new HttpRemoteExecutionClient(
                restTemplate,
                "http://runner/internal/runner-executions"
        );

        server.expect(requestTo("http://runner/internal/runner-executions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "submissionId": "00000000-0000-0000-0000-000000000011",
                          "problemId": "22",
                          "language": "java",
                          "sourceCode": "class Main {}",
                          "input": "1 2",
                          "timeLimitMs": 1000,
                          "memoryLimitMb": 128
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "stdout": "3\\n",
                          "stderr": "",
                          "exitCode": 0,
                          "executionTimeMs": 12,
                          "memoryUsageKb": 512,
                          "systemError": false,
                          "timedOut": false
                        }
                        """, MediaType.APPLICATION_JSON));

        RunnerExecutionResponse response = client.execute(new RunnerExecutionRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                "22",
                "java",
                "class Main {}",
                "1 2",
                1000,
                128
        ));

        server.verify();
        assertThat(response.success()).isTrue();
        assertThat(response.stdout()).isEqualTo("3\n");
        assertThat(response.exitCode()).isEqualTo(0);
        assertThat(response.executionTimeMs()).isEqualTo(12);
        assertThat(response.memoryUsageKb()).isEqualTo(512);
        assertThat(response.systemError()).isFalse();
        assertThat(response.timedOut()).isFalse();
    }
}
