package com.example.ssoj.judge;

import com.example.ssoj.judge.application.sevice.SubmissionProgressHub;
import com.example.ssoj.judge.domain.model.JudgeProgressEvent;
import com.example.ssoj.judge.presentation.JudgeProgressController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JudgeProgressControllerTest {

    @Mock
    private SubmissionProgressHub submissionProgressHub;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new JudgeProgressController(submissionProgressHub))
                .build();
    }

    @Test
    void publish_acceptsProgressAndPublishesToHub() throws Exception {
        mockMvc.perform(post("/internal/judge-progress")
                        .contentType("application/json")
                        .content("""
                                {
                                  "submissionId": 220,
                                  "phase": "RUNNING",
                                  "completedTestcases": 37,
                                  "totalTestcases": 100,
                                  "progressPercent": 37,
                                  "result": null
                                }
                                """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<JudgeProgressEvent> captor = ArgumentCaptor.forClass(JudgeProgressEvent.class);
        verify(submissionProgressHub).publish(captor.capture());
        assertThat(captor.getValue().submissionId()).isEqualTo(220L);
        assertThat(captor.getValue().completedTestcases()).isEqualTo(37);
        assertThat(captor.getValue().result()).isNull();
    }
}
