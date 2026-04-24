package com.example.ssoj.judge;

import com.example.ssoj.judge.infrastructure.remote.HttpRemoteExecutionClient;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;
import com.example.ssoj.judge.presentation.dto.RunnerTestCaseRequest;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpRemoteExecutionClientTest {

    @Test
    void execute_postsSubmissionRequestAndReadsRunResult() {
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
                          "submissionId": 11,
                          "problemId": 22,
                          "language": "java",
                          "sourceCode": "class Main {}",
                          "testCases": [
                            { "testCaseOrder": 1, "input": "1 2", "expectedOutput": "3" }
                          ],
                          "timeLimitMs": 1000,
                          "memoryLimitMb": 128
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "result": "AC",
                          "executionTimeMs": 12,
                          "memoryUsageKb": 512,
                          "failedTestcaseOrder": null
                        }
                        """, MediaType.APPLICATION_JSON));

        RunnerExecutionResponse response = client.execute(new RunnerExecutionRequest(
                11L,
                22L,
                "java",
                "class Main {}",
                List.of(new RunnerTestCaseRequest(1, "1 2", "3")),
                1000,
                128
        ));

        server.verify();
        assertThat(response.result()).isEqualTo(SubmissionResult.AC);
        assertThat(response.executionTimeMs()).isEqualTo(12);
        assertThat(response.memoryUsageKb()).isEqualTo(512);
        assertThat(response.failedTestcaseOrder()).isNull();
    }
}
