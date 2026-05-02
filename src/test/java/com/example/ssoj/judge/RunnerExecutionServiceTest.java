package com.example.ssoj.judge;

import com.example.ssoj.judge.application.selector.RunnerLanguageExecutorSelector;
import com.example.ssoj.judge.application.sevice.RunnerBusyException;
import com.example.ssoj.judge.application.sevice.RunnerExecutionLimiter;
import com.example.ssoj.judge.application.sevice.RunnerExecutionService;
import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.executor.LanguageExecutor;
import com.example.ssoj.judge.infrastructure.config.RunnerExecutionProperties;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;
import com.example.ssoj.judge.presentation.dto.RunnerTestCaseRequest;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class RunnerExecutionServiceTest {

    @Test
    void executeSubmission_usesMatchingExecutorAndMapsRunResult() {
        RecordingLanguageExecutor executor = new RecordingLanguageExecutor(
                "python",
                new JudgeRunResult(SubmissionResult.AC, 12, 256, null)
        );
        RunnerExecutionService service = new RunnerExecutionService(
                new RunnerLanguageExecutorSelector(List.of(executor)),
                limiter(4)
        );

        RunnerExecutionResponse response = service.executeSubmission(request("python"));

        assertThat(response.result()).isEqualTo(SubmissionResult.AC);
        assertThat(response.executionTimeMs()).isEqualTo(12);
        assertThat(response.memoryUsageKb()).isEqualTo(256);
        assertThat(response.failedTestcaseOrder()).isNull();
        assertThat(executor.lastContext()).isEqualTo(new JudgeRunContext(
                10L,
                20L,
                "python",
                "print(42)",
                List.of(
                        new HiddenTestCaseSnapshot(null, 1, "1 2\n", "3\n"),
                        new HiddenTestCaseSnapshot(null, 2, "2 3\n", "5\n")
                ),
                1000,
                128
        ));
    }

    @Test
    void executeSubmission_returnsSystemErrorWhenLanguageIsUnsupported() {
        RunnerExecutionService service = new RunnerExecutionService(
                new RunnerLanguageExecutorSelector(List.of()),
                limiter(4)
        );

        RunnerExecutionResponse response = service.executeSubmission(request("ruby"));

        assertThat(response.result()).isEqualTo(SubmissionResult.SYSTEM_ERROR);
        assertThat(response.failedTestcaseOrder()).isNull();
    }

    @Test
    void executeSubmission_returnsSystemErrorWhenExecutorThrows() {
        RunnerExecutionService service = new RunnerExecutionService(
                new RunnerLanguageExecutorSelector(List.of(new ThrowingLanguageExecutor("cpp"))),
                limiter(4)
        );

        RunnerExecutionResponse response = service.executeSubmission(request("cpp"));

        assertThat(response.result()).isEqualTo(SubmissionResult.SYSTEM_ERROR);
        assertThat(response.failedTestcaseOrder()).isNull();
    }

    @Test
    void executeSubmission_releasesPermitWhenExecutorThrows() {
        RunnerExecutionService service = new RunnerExecutionService(
                new RunnerLanguageExecutorSelector(List.of(new ThrowingLanguageExecutor("cpp"))),
                limiter(1)
        );

        RunnerExecutionResponse firstResponse = service.executeSubmission(request("cpp"));
        RunnerExecutionResponse secondResponse = service.executeSubmission(request("cpp"));

        assertThat(firstResponse.result()).isEqualTo(SubmissionResult.SYSTEM_ERROR);
        assertThat(secondResponse.result()).isEqualTo(SubmissionResult.SYSTEM_ERROR);
    }

    @Test
    void executeSubmission_throwsRunnerBusyWhenNoPermitIsAvailable() throws Exception {
        BlockingLanguageExecutor executor = new BlockingLanguageExecutor("python");
        RunnerExecutionService service = new RunnerExecutionService(
                new RunnerLanguageExecutorSelector(List.of(executor)),
                limiter(1)
        );
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            Future<RunnerExecutionResponse> runningExecution = executorService.submit(
                    () -> service.executeSubmission(request("python"))
            );
            executor.awaitStarted();

            assertThatThrownBy(() -> service.executeSubmission(request("python")))
                    .isInstanceOf(RunnerBusyException.class)
                    .hasMessage("Runner is busy. Please retry later.");

            executor.release();
            assertThat(runningExecution.get().result()).isEqualTo(SubmissionResult.AC);
        } finally {
            executor.release();
            executorService.shutdownNow();
        }
    }

    private RunnerExecutionRequest request(String language) {
        return new RunnerExecutionRequest(
                10L,
                20L,
                language,
                "print(42)",
                List.of(
                        new RunnerTestCaseRequest(1, "1 2\n", "3\n"),
                        new RunnerTestCaseRequest(2, "2 3\n", "5\n")
                ),
                1000,
                128
        );
    }

    private RunnerExecutionLimiter limiter(int maxConcurrentExecutions) {
        return new RunnerExecutionLimiter(new RunnerExecutionProperties(maxConcurrentExecutions));
    }

    private static final class RecordingLanguageExecutor implements LanguageExecutor {
        private final String language;
        private final JudgeRunResult result;
        private JudgeRunContext lastContext;

        private RecordingLanguageExecutor(String language, JudgeRunResult result) {
            this.language = language;
            this.result = result;
        }

        @Override
        public boolean supports(String language) {
            return this.language.equalsIgnoreCase(language);
        }

        @Override
        public JudgeRunResult executeSubmission(JudgeRunContext context) {
            this.lastContext = context;
            return result;
        }

        private JudgeRunContext lastContext() {
            return lastContext;
        }
    }

    private static final class ThrowingLanguageExecutor implements LanguageExecutor {
        private final String language;

        private ThrowingLanguageExecutor(String language) {
            this.language = language;
        }

        @Override
        public boolean supports(String language) {
            return this.language.equalsIgnoreCase(language);
        }

        @Override
        public JudgeRunResult executeSubmission(JudgeRunContext context) {
            throw new IllegalStateException("docker unavailable");
        }
    }

    private static final class BlockingLanguageExecutor implements LanguageExecutor {
        private final String language;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingLanguageExecutor(String language) {
            this.language = language;
        }

        @Override
        public boolean supports(String language) {
            return this.language.equalsIgnoreCase(language);
        }

        @Override
        public JudgeRunResult executeSubmission(JudgeRunContext context) {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", exception);
            }
            return new JudgeRunResult(SubmissionResult.AC, 1, 1, null);
        }

        private void awaitStarted() throws InterruptedException {
            started.await();
        }

        private void release() {
            release.countDown();
        }
    }
}
