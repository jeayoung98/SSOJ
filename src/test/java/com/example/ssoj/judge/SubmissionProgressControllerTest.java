package com.example.ssoj.judge;

import com.example.ssoj.judge.application.sevice.SubmissionProgressHub;
import com.example.ssoj.judge.presentation.SubmissionProgressController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubmissionProgressControllerTest {

    @Mock
    private SubmissionProgressHub submissionProgressHub;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SubmissionProgressController(submissionProgressHub))
                .build();
    }

    @Test
    void subscribe_returnsSseEmitter() throws Exception {
        when(submissionProgressHub.subscribe(220L)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/submissions/220/events"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(submissionProgressHub).subscribe(220L);
    }
}
