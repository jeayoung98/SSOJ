package com.example.ssoj.judge;

import com.example.ssoj.problem.domain.Problem;
import com.example.ssoj.problem.infrastructure.ProblemRepository;
import com.example.ssoj.submission.domain.Submission;
import com.example.ssoj.submission.domain.SubmissionResult;
import com.example.ssoj.submission.infrastructure.SubmissionRepository;
import com.example.ssoj.submission.domain.SubmissionStatus;
import com.example.ssoj.testcase.domain.ProblemTestcase;
import com.example.ssoj.testcase.infrastructure.ProblemTestcaseRepository;
import com.example.ssoj.user.domain.User;
import com.example.ssoj.user.infrastructure.UserRepository;
import com.example.ssoj.judge.application.sevice.JudgeQueueConsumer;
import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.executor.LanguageExecutor;
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
import java.util.UUID;
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
    private ProblemTestcaseRepository testCaseRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JudgeQueueConsumer judgeQueueConsumer;

    @Autowired
    private FakeLanguageExecutor fakeLanguageExecutor;

    @AfterEach
    void tearDown() {
        submissionRepository.deleteAll();
        userRepository.deleteAll();
        testCaseRepository.deleteAll();
        problemRepository.deleteAll();
        fakeLanguageExecutor.reset();
    }

    @Test
    void consume_readsRedisQueueAndPersistsResultsAgainstRealRedisAndPostgres() throws InterruptedException {
        Problem problem = problemRepository.save(problem(1000, 128));
        User user = userRepository.save(user());
        testCaseRepository.save(testCase(problem, "1 2", "3\n", true));

        Submission submission = submissionRepository.save(submission(user, problem, "fake", "print()", SubmissionStatus.PENDING));
        fakeLanguageExecutor.setResult(new JudgeExecutionResult(true, "3\n", "", 0, 11, 128, false, false, false, false));

        stringRedisTemplate.opsForList().leftPush(QUEUE_KEY, submission.getId().toString());
        judgeQueueConsumer.consume();

        Submission finishedSubmission = awaitSubmission(submission.getId(), SubmissionResult.AC, Duration.ofSeconds(5));

        assertThat(finishedSubmission.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(finishedSubmission.getResult()).isEqualTo(SubmissionResult.AC);
        assertThat(finishedSubmission.getFailedTestcaseOrder()).isNull();
        assertThat(finishedSubmission.getExecutionTimeMs()).isEqualTo(11);
        assertThat(finishedSubmission.getMemoryKb()).isEqualTo(128);
        assertThat(finishedSubmission.getJudgedAt()).isNotNull();
    }

    private Submission awaitSubmission(Long submissionId, SubmissionResult expectedResult, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Submission submission = submissionRepository.findById(submissionId).orElseThrow();
            if (submission.getResult() == expectedResult) {
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
        ReflectionTestUtils.setField(problem, "difficulty", "EASY");
        ReflectionTestUtils.setField(problem, "description", "sum two numbers");
        ReflectionTestUtils.setField(problem, "timeLimitMs", timeLimitMs);
        ReflectionTestUtils.setField(problem, "memoryLimitMb", memoryLimitMb);
        return problem;
    }

    private static User user() {
        User user = instantiate(User.class);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "nickname", "tester");
        ReflectionTestUtils.setField(user, "role", "USER");
        return user;
    }

    private static ProblemTestcase testCase(Problem problem, String input, String output, boolean hidden) {
        ProblemTestcase testCase = instantiate(ProblemTestcase.class);
        ReflectionTestUtils.setField(testCase, "problem", problem);
        ReflectionTestUtils.setField(testCase, "testcaseOrder", 1);
        ReflectionTestUtils.setField(testCase, "inputText", input);
        ReflectionTestUtils.setField(testCase, "expectedOutput", output);
        ReflectionTestUtils.setField(testCase, "hidden", hidden);
        return testCase;
    }

    private static Submission submission(User user, Problem problem, String language, String sourceCode, SubmissionStatus status) {
        Submission submission = instantiate(Submission.class);
        ReflectionTestUtils.setField(submission, "user", user);
        ReflectionTestUtils.setField(submission, "problem", problem);
        ReflectionTestUtils.setField(submission, "language", language);
        ReflectionTestUtils.setField(submission, "sourceCode", sourceCode);
        ReflectionTestUtils.setField(submission, "status", status);
        ReflectionTestUtils.setField(submission, "submittedAt", Instant.now());
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
