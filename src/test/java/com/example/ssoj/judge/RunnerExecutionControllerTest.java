package com.example.ssoj.judge;

import com.example.ssoj.judge.application.sevice.RunnerExecutionService;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;
import com.example.ssoj.judge.presentation.RunnerExecutionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
    void execute_acceptsRunnerRequestAndReturnsRunnerResponse() throws Exception {
        RunnerExecutionRequest request = new RunnerExecutionRequest(
                123L,
                456L,
                "python",
                "print(1)",
                "",
                1000,
                128
        );
        when(runnerExecutionService.execute(request)).thenReturn(new RunnerExecutionResponse(
                true,
                "1\n",
                "",
                0,
                9,
                128,
                false,
                false,
                false,
                false
        ));

        mockMvc.perform(post("/internal/runner-executions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "submissionId": 123,
                                  "problemId": 456,
                                  "language": "python",
                                  "sourceCode": "print(1)",
                                  "input": "",
                                  "timeLimitMs": 1000,
                                  "memoryLimitMb": 128
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.stdout").value("1\n"))
                .andExpect(jsonPath("$.systemError").value(false));

        verify(runnerExecutionService).execute(request);
    }
}
