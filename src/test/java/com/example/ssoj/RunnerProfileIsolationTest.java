package com.example.ssoj;

import com.example.ssoj.judge.presentation.RunnerExecutionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=runner",
        "worker.executor.cpp.image=test-gcc",
        "worker.executor.java.image=test-java",
        "worker.executor.python.image=test-python"
})
class RunnerProfileIsolationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void runnerProfileStartsWithoutDatabaseOrRedisBeans() {
        assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(StringRedisTemplate.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(RunnerExecutionController.class)).hasSize(1);
    }

    @Test
    void runnerExecutionEndpointIsAvailable() throws Exception {
        mockMvc.perform(post("/internal/runner-executions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "submissionId": "00000000-0000-0000-0000-000000000001",
                                  "problemId": "1",
                                  "language": "unsupported",
                                  "sourceCode": "print(1)",
                                  "input": "",
                                  "timeLimitMs": 1000,
                                  "memoryLimitMb": 128
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.systemError").value(true));
    }
}
