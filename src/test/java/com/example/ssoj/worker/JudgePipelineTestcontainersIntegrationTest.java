package com.example.ssoj.worker;

import com.example.ssoj.problem.Problem;
import com.example.ssoj.problem.ProblemRepository;
import com.example.ssoj.submission.Submission;
import com.example.ssoj.submission.SubmissionCaseResult;
import com.example.ssoj.submission.SubmissionCaseResultRepository;
import com.example.ssoj.submission.SubmissionRepository;
import com.example.ssoj.submission.SubmissionStatus;
import com.example.ssoj.testcase.TestCase;
import com.example.ssoj.testcase.TestCaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "worker.enabled=true",
        "worker.poll-delay-ms=60000"
})
@Import(JudgePipelineTestcontainersIntegrationTest.TestExecutorConfig.class)
@EnabledIfSystemProperty(named = "run.testcontainers.tests", matches = "true")
class JudgePipelineTestcontainersIntegrationTest {

    private static final String QUEUE_KEY = "judge:queue";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SubmissionCaseResultRepository submissionCaseResultRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JudgeQueueConsumer judgeQueueConsumer;

    @Autowired
    private FakeLanguageExecutor fakeLanguageExecutor;

    @AfterEach
    void tearDown() {
        submissionCaseResultRepository.deleteAll();
        submissionRepository.deleteAll();
        testCaseRepository.deleteAll();
        problemRepository.deleteAll();
        fakeLanguageExecutor.reset();
    }

    @Test
    void consume_readsRedisQueueAndPersistsResultsAgainstRealRedisAndPostgres() throws InterruptedException {
        Problem problem = problemRepository.save(problem(1000, 128));
        testCaseRepository.save(testCase(problem, "1 2", "3\n", true));

        Submission submission = submissionRepository.save(submission(problem, "fake", "print()", SubmissionStatus.PENDING));
        fakeLanguageExecutor.setResult(new JudgeExecutionResult(true, "3\n", "", 0, 11, 128, false, false));

        stringRedisTemplate.opsForList().leftPush(QUEUE_KEY, submission.getId().toString());
        judgeQueueConsumer.consume();

        Submission finishedSubmission = awaitSubmission(submission.getId(), SubmissionStatus.AC, Duration.ofSeconds(5));
        List<SubmissionCaseResult> caseResults = submissionCaseResultRepository.findAll();

        assertThat(finishedSubmission.getStatus()).isEqualTo(SubmissionStatus.AC);
        assertThat(finishedSubmission.getStartedAt()).isNotNull();
        assertThat(finishedSubmission.getFinishedAt()).isNotNull();
        assertThat(caseResults).hasSize(1);
        assertThat(caseResults.get(0).getStatus()).isEqualTo(SubmissionStatus.AC);
    }

    private Submission awaitSubmission(Long submissionId, SubmissionStatus expectedStatus, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Submission submission = submissionRepository.findById(submissionId).orElseThrow();
            if (submission.getStatus() == expectedStatus) {
                return submission;
            }
            Thread.sleep(100);
        }

        Submission current = submissionRepository.findById(submissionId).orElseThrow();
        throw new AssertionError("Timed out waiting for submission status. currentStatus=" + current.getStatus());
    }

    private static Problem problem(int timeLimitMs, int memoryLimitMb) {
        Problem problem = instantiate(Problem.class);
        ReflectionTestUtils.setField(problem, "title", "A + B");
        ReflectionTestUtils.setField(problem, "description", "sum two numbers");
        ReflectionTestUtils.setField(problem, "timeLimitMs", timeLimitMs);
        ReflectionTestUtils.setField(problem, "memoryLimitMb", memoryLimitMb);
        return problem;
    }

    private static TestCase testCase(Problem problem, String input, String output, boolean hidden) {
        TestCase testCase = instantiate(TestCase.class);
        ReflectionTestUtils.setField(testCase, "problem", problem);
        ReflectionTestUtils.setField(testCase, "input", input);
        ReflectionTestUtils.setField(testCase, "output", output);
        ReflectionTestUtils.setField(testCase, "hidden", hidden);
        return testCase;
    }

    private static Submission submission(Problem problem, String language, String sourceCode, SubmissionStatus status) {
        Submission submission = instantiate(Submission.class);
        ReflectionTestUtils.setField(submission, "problem", problem);
        ReflectionTestUtils.setField(submission, "language", language);
        ReflectionTestUtils.setField(submission, "sourceCode", sourceCode);
        ReflectionTestUtils.setField(submission, "status", status);
        ReflectionTestUtils.setField(submission, "createdAt", Instant.now());
        return submission;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to instantiate " + type.getName(), exception);
        }
    }

    @TestConfiguration
    static class TestExecutorConfig {

        @Bean
        @Primary
        FakeLanguageExecutor fakeLanguageExecutor() {
            return new FakeLanguageExecutor();
        }
    }

    static class FakeLanguageExecutor implements LanguageExecutor {

        private final AtomicReference<JudgeExecutionResult> result = new AtomicReference<>(JudgeExecutionResult.notExecuted());

        @Override
        public boolean supports(String language) {
            return "fake".equalsIgnoreCase(language);
        }

        @Override
        public JudgeExecutionResult execute(JudgeContext context) {
            return result.get();
        }

        void setResult(JudgeExecutionResult judgeExecutionResult) {
            result.set(judgeExecutionResult);
        }

        void reset() {
            result.set(JudgeExecutionResult.notExecuted());
        }
    }
}
