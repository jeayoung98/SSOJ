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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:judge-pipeline;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
    private TestCaseRepository testCaseRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SubmissionCaseResultRepository submissionCaseResultRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ListOperations<String, String> listOperations;

    @Autowired
    private JudgeQueueConsumer judgeQueueConsumer;

    @Autowired
    private FakeLanguageExecutor fakeLanguageExecutor;

    @AfterEach
    void tearDown() {
        // 같은 ApplicationContext를 재사용하므로 mock 상태와 DB 데이터를 매 테스트마다 초기화한다.
        reset(listOperations);
        submissionCaseResultRepository.deleteAll();
        submissionRepository.deleteAll();
        testCaseRepository.deleteAll();
        problemRepository.deleteAll();
        fakeLanguageExecutor.reset();
    }

    @Test
    void consume_readsQueueAndPersistsJudgeResult() throws InterruptedException {
        // Redis consume부터 DB 결과 저장까지 최소 파이프라인을 검증한다.
        Problem problem = problemRepository.save(problem(1000, 128));
        TestCase hiddenCase = testCase(problem, "1 2", "3\n", true);
        testCaseRepository.save(hiddenCase);

        Submission submission = submissionRepository.save(submission(problem, "fake", "print()", SubmissionStatus.PENDING));
        fakeLanguageExecutor.setResult(new JudgeExecutionResult(true, "3\n", "", 0, 7, 256, false, false));

        when(listOperations.leftPop(QUEUE_KEY)).thenReturn(submission.getId().toString());
        judgeQueueConsumer.consume();

        Submission finishedSubmission = awaitSubmission(submission.getId(), SubmissionStatus.AC, Duration.ofSeconds(5));
        List<SubmissionCaseResult> caseResults = submissionCaseResultRepository.findAll();

        assertThat(finishedSubmission.getStatus()).isEqualTo(SubmissionStatus.AC);
        assertThat(finishedSubmission.getStartedAt()).isNotNull();
        assertThat(finishedSubmission.getFinishedAt()).isNotNull();
        assertThat(caseResults).hasSize(1);
        assertThat(caseResults.get(0).getStatus()).isEqualTo(SubmissionStatus.AC);
        assertThat(caseResults.get(0).getExecutionTimeMs()).isEqualTo(7);
        assertThat(fakeLanguageExecutor.lastContext()).isNotNull();
        assertThat(fakeLanguageExecutor.lastContext().submissionId()).isEqualTo(submission.getId());
        assertThat(fakeLanguageExecutor.lastContext().problemId()).isEqualTo(problem.getId());
    }

    @Test
    void consume_persistsTleResultWhenExecutorTimesOut() throws InterruptedException {
        // executor timeout 결과가 submission과 case result에 그대로 반영되어야 한다.
        Problem problem = problemRepository.save(problem(1000, 128));
        TestCase hiddenCase = testCase(problem, "1 2", "3\n", true);
        testCaseRepository.save(hiddenCase);

        Submission submission = submissionRepository.save(submission(problem, "fake", "print()", SubmissionStatus.PENDING));
        fakeLanguageExecutor.setResult(JudgeExecutionResult.timeout(3000));

        when(listOperations.leftPop(QUEUE_KEY)).thenReturn(submission.getId().toString());
        judgeQueueConsumer.consume();

        Submission finishedSubmission = awaitSubmission(submission.getId(), SubmissionStatus.TLE, Duration.ofSeconds(5));
        List<SubmissionCaseResult> caseResults = submissionCaseResultRepository.findAll();

        assertThat(finishedSubmission.getStatus()).isEqualTo(SubmissionStatus.TLE);
        assertThat(caseResults).hasSize(1);
        assertThat(caseResults.get(0).getStatus()).isEqualTo(SubmissionStatus.TLE);
    }

    private Submission awaitSubmission(Long submissionId, SubmissionStatus expectedStatus, Duration timeout)
            throws InterruptedException {
        // JudgeQueueConsumer는 별도 스레드 풀을 사용하므로 상태 반영을 polling으로 기다린다.
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
            // 테스트에서는 엔티티 생성 편의성을 위해 리플렉션으로 최소 필드만 채운다.
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
            // 실제 Redis 없이 queue consume 경로만 검증할 수 있도록 템플릿 전체를 mock 처리한다.
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
        // 언어 executor 결과를 테스트가 원하는 값으로 고정하기 위한 대역 객체다.

        private final AtomicReference<JudgeExecutionResult> result = new AtomicReference<>(JudgeExecutionResult.notExecuted());
        private final AtomicReference<JudgeContext> lastContext = new AtomicReference<>();

        @Override
        public boolean supports(String language) {
            return "fake".equalsIgnoreCase(language);
        }

        @Override
        public JudgeExecutionResult execute(JudgeContext context) {
            lastContext.set(context);
            return result.get();
        }

        void setResult(JudgeExecutionResult judgeExecutionResult) {
            result.set(judgeExecutionResult);
        }

        JudgeContext lastContext() {
            return lastContext.get();
        }

        void reset() {
            result.set(JudgeExecutionResult.notExecuted());
            lastContext.set(null);
        }
    }
}
