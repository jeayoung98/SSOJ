package com.example.ssoj.judge;

import com.example.ssoj.judge.application.sevice.RunnerExecutionService;
import com.example.ssoj.judge.presentation.RunnerExecutionController;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;
import com.example.ssoj.judge.presentation.dto.RunnerTestCaseRequest;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RunnerExecutionControllerTest {

    @Mock
    private RunnerExecutionService runnerExecutionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RunnerExecutionController(runnerExecutionService))
                .build();
    }

    @Test
    void execute_acceptsSubmissionRequestAndReturnsRunResult() throws Exception {
        RunnerExecutionRequest request = new RunnerExecutionRequest(
                123L,
                456L,
                "python",
                "print(1)",
                List.of(new RunnerTestCaseRequest(1, "", "1\n")),
                1000,
                128
        );
        when(runnerExecutionService.executeSubmission(request))
                .thenReturn(new RunnerExecutionResponse(SubmissionResult.AC, 9, 128, null));

        mockMvc.perform(post("/internal/runner-executions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "submissionId": 123,
                                  "problemId": 456,
                                  "language": "python",
                                  "sourceCode": "print(1)",
                                  "testCases": [
                                    { "testCaseOrder": 1, "input": "", "expectedOutput": "1\\n" }
                                  ],
                                  "timeLimitMs": 1000,
                                  "memoryLimitMb": 128
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("AC"))
                .andExpect(jsonPath("$.executionTimeMs").value(9))
                .andExpect(jsonPath("$.memoryUsageKb").value(128));

        verify(runnerExecutionService).executeSubmission(request);
    }
}
