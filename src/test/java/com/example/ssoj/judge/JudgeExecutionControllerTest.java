package com.example.ssoj.judge;

import com.example.ssoj.judge.application.sevice.JudgeService;
import com.example.ssoj.judge.presentation.JudgeExecutionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JudgeExecutionControllerTest {

    @Mock
    private JudgeService judgeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new JudgeExecutionController(judgeService))
                .build();
    }

    @Test
    void execute_acceptsSubmissionIdAndTriggersJudgeService() throws Exception {
        mockMvc.perform(post("/internal/judge-executions")
                        .contentType("application/json")
                        .content("""
                                {"submissionId":123}
                                """))
                .andExpect(status().isAccepted());

        verify(judgeService).judge(123L);
    }
}
