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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:judge-pipeline;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:/h2-init.sql'",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "worker.enabled=true",
        "worker.poll-delay-ms=60000"
})
@Import(JudgePipelineIntegrationTest.TestExecutorConfig.class)
class JudgePipelineIntegrationTest {

    private static final String QUEUE_KEY = "judge:queue";

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemTestcaseRepository testCaseRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ListOperations<String, String> listOperations;

    @Autowired
    private JudgeQueueConsumer judgeQueueConsumer;

    @Autowired
    private FakeLanguageExecutor fakeLanguageExecutor;

    @AfterEach
    void tearDown() {
        reset(listOperations);
        submissionRepository.deleteAll();
        userRepository.deleteAll();
        testCaseRepository.deleteAll();
        problemRepository.deleteAll();
        fakeLanguageExecutor.reset();
    }

    @Test
    void consume_finishesSubmissionAndPersistsAcCaseResult() throws InterruptedException {
        Problem problem = problemRepository.save(problem(1000, 128));
        User user = userRepository.save(user());
        testCaseRepository.save(testCase(problem, "1 2", "3\n", true));

        Submission submission = submissionRepository.save(submission(user, problem, "fake", "print()", SubmissionStatus.PENDING));
        fakeLanguageExecutor.setResults(new JudgeExecutionResult(true, "3\n", "", 0, 7, 256, false, false));

        when(listOperations.leftPop(QUEUE_KEY)).thenReturn(submission.getId().toString());
        judgeQueueConsumer.consume();

        Submission finishedSubmission = awaitSubmission(submission.getId(), SubmissionResult.AC, Duration.ofSeconds(5));

        assertThat(finishedSubmission.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(finishedSubmission.getResult()).isEqualTo(SubmissionResult.AC);
        assertThat(finishedSubmission.getFailedTestcaseOrder()).isNull();
        assertThat(finishedSubmission.getExecutionTimeMs()).isEqualTo(7);
        assertThat(finishedSubmission.getMemoryKb()).isEqualTo(256);
        assertThat(finishedSubmission.getJudgedAt()).isNotNull();
        assertThat(fakeLanguageExecutor.executedContexts()).hasSize(1);
        assertThat(fakeLanguageExecutor.lastContext()).isNotNull();
        assertThat(fakeLanguageExecutor.lastContext().submissionId()).isEqualTo(submission.getId());
        assertThat(fakeLanguageExecutor.lastContext().problemId()).isEqualTo(problem.getId());
    }

    @Test
    void consume_finishesSubmissionWithTleWhenFirstHiddenCaseTimesOut() throws InterruptedException {
        Problem problem = problemRepository.save(problem(1000, 128));
        User user = userRepository.save(user());
        testCaseRepository.save(testCase(problem, "1 2", "3\n", true));

        Submission submission = submissionRepository.save(submission(user, problem, "fake", "print()", SubmissionStatus.PENDING));
        fakeLanguageExecutor.setResults(JudgeExecutionResult.timeout(3000));

        when(listOperations.leftPop(QUEUE_KEY)).thenReturn(submission.getId().toString());
        judgeQueueConsumer.consume();

        Submission finishedSubmission = awaitSubmission(submission.getId(), SubmissionResult.TLE, Duration.ofSeconds(5));

        assertThat(finishedSubmission.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(finishedSubmission.getResult()).isEqualTo(SubmissionResult.TLE);
        assertThat(finishedSubmission.getFailedTestcaseOrder()).isEqualTo(1);
        assertThat(finishedSubmission.getExecutionTimeMs()).isEqualTo(3000);
        assertThat(finishedSubmission.getJudgedAt()).isNotNull();
        assertThat(fakeLanguageExecutor.executedContexts()).hasSize(1);
    }

    @Test
    void consume_stopsAfterFirstHiddenCaseFailure_andDoesNotPersistLaterCases() throws InterruptedException {
        Problem problem = problemRepository.save(problem(1000, 128));
        User user = userRepository.save(user());
        testCaseRepository.save(testCase(problem, 1, "1", "1\n", true));
        testCaseRepository.save(testCase(problem, 2, "2", "2\n", true));
        testCaseRepository.save(testCase(problem, 3, "3", "3\n", true));

        Submission submission = submissionRepository.save(submission(user, problem, "fake", "print()", SubmissionStatus.PENDING));
        fakeLanguageExecutor.setResults(
                new JudgeExecutionResult(true, "1\n", "", 0, 5, 128, false, false),
                new JudgeExecutionResult(true, "wrong\n", "", 0, 6, 64, false, false),
                new JudgeExecutionResult(true, "3\n", "", 0, 7, 64, false, false)
        );

        when(listOperations.leftPop(QUEUE_KEY)).thenReturn(submission.getId().toString());
        judgeQueueConsumer.consume();

        Submission finishedSubmission = awaitSubmission(submission.getId(), SubmissionResult.WA, Duration.ofSeconds(5));

        assertThat(finishedSubmission.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(finishedSubmission.getResult()).isEqualTo(SubmissionResult.WA);
        assertThat(finishedSubmission.getFailedTestcaseOrder()).isEqualTo(2);
        assertThat(finishedSubmission.getExecutionTimeMs()).isEqualTo(6);
        assertThat(finishedSubmission.getMemoryKb()).isEqualTo(128);
        assertThat(finishedSubmission.getJudgedAt()).isNotNull();
        assertThat(fakeLanguageExecutor.executedContexts()).hasSize(2);
    }

    private Submission awaitSubmission(UUID submissionId, SubmissionResult expectedResult, Duration timeout)
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
        ReflectionTestUtils.setField(problem, "id", UUID.randomUUID().toString());
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
        return testCase(problem, 1, input, output, hidden);
    }

    private static ProblemTestcase testCase(Problem problem, int order, String input, String output, boolean hidden) {
        ProblemTestcase testCase = instantiate(ProblemTestcase.class);
        ReflectionTestUtils.setField(testCase, "problem", problem);
        ReflectionTestUtils.setField(testCase, "testcaseOrder", order);
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
        FakeLanguageExecutor fakeLanguageExecutor() {
            return new FakeLanguageExecutor();
        }

        @Bean
        @Primary
        ListOperations<String, String> listOperations() {
            return mock(ListOperations.class);
        }

        @Bean
        @Primary
        StringRedisTemplate stringRedisTemplate(ListOperations<String, String> listOperations) {
            RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
            RedisConnection connection = mock(RedisConnection.class);
            when(connectionFactory.getConnection()).thenReturn(connection);
            when(connection.ping()).thenReturn("PONG");

            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
            return redisTemplate;
        }
    }

    static class FakeLanguageExecutor implements LanguageExecutor {

        private final AtomicReference<JudgeExecutionResult> fallbackResult =
                new AtomicReference<>(JudgeExecutionResult.notExecuted());
        private final ConcurrentLinkedQueue<JudgeExecutionResult> results = new ConcurrentLinkedQueue<>();
        private final AtomicReference<JudgeContext> lastContext = new AtomicReference<>();
        private final List<JudgeContext> executedContexts = new ArrayList<>();

        @Override
        public boolean supports(String language) {
            return "fake".equalsIgnoreCase(language);
        }

        @Override
        public synchronized JudgeExecutionResult execute(JudgeContext context) {
            lastContext.set(context);
            executedContexts.add(context);

            JudgeExecutionResult nextResult = results.poll();
            if (nextResult != null) {
                return nextResult;
            }

            return fallbackResult.get();
        }

        void setResults(JudgeExecutionResult... judgeExecutionResults) {
            results.clear();
            for (JudgeExecutionResult judgeExecutionResult : judgeExecutionResults) {
                results.add(judgeExecutionResult);
            }

            if (judgeExecutionResults.length > 0) {
                fallbackResult.set(judgeExecutionResults[judgeExecutionResults.length - 1]);
            } else {
                fallbackResult.set(JudgeExecutionResult.notExecuted());
            }
        }

        JudgeContext lastContext() {
            return lastContext.get();
        }

        synchronized List<JudgeContext> executedContexts() {
            return List.copyOf(executedContexts);
        }

        void reset() {
            fallbackResult.set(JudgeExecutionResult.notExecuted());
            results.clear();
            lastContext.set(null);
            executedContexts.clear();
        }
    }
}
