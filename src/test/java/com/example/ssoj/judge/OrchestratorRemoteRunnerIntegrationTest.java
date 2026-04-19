package com.example.ssoj.judge;

import com.example.ssoj.problem.domain.Problem;
import com.example.ssoj.problem.infrastructure.ProblemRepository;
import com.example.ssoj.submission.domain.Submission;
import com.example.ssoj.submission.domain.SubmissionCaseResult;
import com.example.ssoj.submission.infrastructure.SubmissionCaseResultRepository;
import com.example.ssoj.submission.infrastructure.SubmissionRepository;
import com.example.ssoj.submission.domain.SubmissionStatus;
import com.example.ssoj.testcase.domain.TestCase;
import com.example.ssoj.testcase.infrastructure.TestCaseRepository;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:orchestrator-remote-runner;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "worker.role=orchestrator",
                "worker.mode=http-trigger",
                "judge.dispatch.mode=redis",
                "judge.execution.mode=remote"
        }
)
class OrchestratorRemoteRunnerIntegrationTest {

    private static final List<RunnerExecutionRequest> RECEIVED_REQUESTS = new CopyOnWriteArrayList<>();

    private static HttpServer runnerServer;

    @LocalServerPort
    private int orchestratorPort;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SubmissionCaseResultRepository submissionCaseResultRepository;

    @DynamicPropertySource
    static void remoteRunnerProperties(DynamicPropertyRegistry registry) throws IOException {
        ensureRunnerServerStarted();
        registry.add("judge.execution.remote.base-url", () -> "http://localhost:" + runnerServer.getAddress().getPort());
        registry.add("judge.execution.remote.execute-path", () -> "/internal/runner-executions");
    }

    @AfterAll
    static void stopRunnerServer() {
        if (runnerServer != null) {
            runnerServer.stop(0);
        }
    }

    @AfterEach
    void tearDown() {
        RECEIVED_REQUESTS.clear();
        submissionCaseResultRepository.deleteAll();
        submissionRepository.deleteAll();
        testCaseRepository.deleteAll();
        problemRepository.deleteAll();
    }

    @Test
    void judgeExecutionController_runsRemoteRunnerForEachHiddenCase_andPersistsFinalResult() {
        Problem problem = problemRepository.save(problem(1000, 128));
        testCaseRepository.save(testCase(problem, "1 2\n", "3\n", true));
        testCaseRepository.save(testCase(problem, "2 3\n", "5\n", true));

        Submission submission = submissionRepository.save(submission(problem, "python", "print(sum(map(int, input().split())))"));
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        ResponseEntity<Void> response = new RestTemplate().postForEntity(
                "http://localhost:" + orchestratorPort + "/internal/judge-executions",
                new HttpEntity<>("{\"submissionId\":" + submission.getId() + "}", headers),
                Void.class
        );

        Submission finishedSubmission = submissionRepository.findById(submission.getId()).orElseThrow();
        List<SubmissionCaseResult> caseResults = submissionCaseResultRepository.findAll().stream()
                .filter(result -> result.getSubmission().getId().equals(submission.getId()))
                .sorted(Comparator.comparing(SubmissionCaseResult::getId))
                .toList();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(finishedSubmission.getStatus()).isEqualTo(SubmissionStatus.AC);
        assertThat(finishedSubmission.getStartedAt()).isNotNull();
        assertThat(finishedSubmission.getFinishedAt()).isNotNull();
        assertThat(caseResults).hasSize(2);
        assertThat(caseResults)
                .extracting(SubmissionCaseResult::getStatus)
                .containsExactly(SubmissionStatus.AC, SubmissionStatus.AC);
        assertThat(RECEIVED_REQUESTS).hasSize(2);
        assertThat(RECEIVED_REQUESTS)
                .extracting(RunnerExecutionRequest::input)
                .containsExactly("1 2\n", "2 3\n");
        assertThat(RECEIVED_REQUESTS)
                .extracting(RunnerExecutionRequest::language)
                .containsOnly("python");
    }

    private static void ensureRunnerServerStarted() throws IOException {
        if (runnerServer != null) {
            return;
        }

        runnerServer = HttpServer.create(new InetSocketAddress(0), 0);
        runnerServer.createContext("/internal/runner-executions", OrchestratorRemoteRunnerIntegrationTest::handleRunnerExecution);
        runnerServer.start();
    }

    private static void handleRunnerExecution(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(HttpStatus.METHOD_NOT_ALLOWED.value(), -1);
                return;
            }

            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            RunnerExecutionRequest request = new RunnerExecutionRequest(
                    longField(requestBody, "submissionId"),
                    longField(requestBody, "problemId"),
                    stringField(requestBody, "language"),
                    stringField(requestBody, "sourceCode"),
                    stringField(requestBody, "input"),
                    intField(requestBody, "timeLimitMs"),
                    intField(requestBody, "memoryLimitMb")
            );
            RECEIVED_REQUESTS.add(request);

            int sum = parseSum(request.input());
            byte[] body = responseJson(sum).getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(HttpStatus.OK.value(), body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private static String responseJson(int sum) {
        return """
                {
                  "success": true,
                  "stdout": "%d\\n",
                  "stderr": "",
                  "exitCode": 0,
                  "executionTimeMs": 5,
                  "memoryUsageKb": 128,
                  "systemError": false,
                  "timedOut": false
                }
                """.formatted(sum);
    }

    private static int parseSum(String input) {
        String[] tokens = input.trim().split("\\s+");
        int total = 0;
        for (String token : tokens) {
            total += Integer.parseInt(token);
        }
        return total;
    }

    private static Long longField(String json, String fieldName) {
        return Long.parseLong(numberToken(json, fieldName));
    }

    private static Integer intField(String json, String fieldName) {
        return Integer.parseInt(numberToken(json, fieldName));
    }

    private static String numberToken(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException("Missing field: " + fieldName);
        }

        int valueStart = start + marker.length();
        while (Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        int valueEnd = valueStart;
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd);
    }

    private static String stringField(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException("Missing field: " + fieldName);
        }

        int index = start + marker.length();
        StringBuilder builder = new StringBuilder();
        while (index < json.length()) {
            char current = json.charAt(index);
            if (current == '\\') {
                char escaped = json.charAt(index + 1);
                if (escaped == 'n') {
                    builder.append('\n');
                } else {
                    builder.append(escaped);
                }
                index += 2;
                continue;
            }
            if (current == '"') {
                break;
            }
            builder.append(current);
            index++;
        }
        return builder.toString();
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

    private static Submission submission(Problem problem, String language, String sourceCode) {
        Submission submission = instantiate(Submission.class);
        ReflectionTestUtils.setField(submission, "problem", problem);
        ReflectionTestUtils.setField(submission, "language", language);
        ReflectionTestUtils.setField(submission, "sourceCode", sourceCode);
        ReflectionTestUtils.setField(submission, "status", SubmissionStatus.PENDING);
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
}
