package com.example.ssoj.judge;

import com.example.ssoj.judge.application.selector.RunnerLanguageExecutorSelector;
import com.example.ssoj.judge.application.sevice.RunnerExecutionService;
import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.executor.LanguageExecutor;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionRequest;
import com.example.ssoj.judge.presentation.dto.RunnerExecutionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RunnerExecutionServiceTest {

    @Test
    void execute_usesMatchingLanguageExecutorAndMapsResponse() {
        RecordingLanguageExecutor executor = new RecordingLanguageExecutor(
                "python",
                new JudgeExecutionResult(true, "42\n", "", 0, 12, 256, false, false)
        );
        RunnerExecutionService service = new RunnerExecutionService(
                new RunnerLanguageExecutorSelector(java.util.List.of(executor))
        );

        RunnerExecutionResponse response = service.execute(new RunnerExecutionRequest(
                10L,
                20L,
                "python",
                "print(42)",
                "",
                1000,
                128
        ));

        assertThat(response.success()).isTrue();
        assertThat(response.stdout()).isEqualTo("42\n");
        assertThat(response.systemError()).isFalse();
        assertThat(executor.lastContext()).isEqualTo(new JudgeContext(10L, 20L, "python", "print(42)", "", 1000, 128));
    }

    @Test
    void execute_returnsSystemErrorWhenLanguageIsUnsupported() {
        RunnerExecutionService service = new RunnerExecutionService(
                new RunnerLanguageExecutorSelector(java.util.List.of())
        );

        RunnerExecutionResponse response = service.execute(new RunnerExecutionRequest(
                10L,
                20L,
                "ruby",
                "puts 1",
                "",
                1000,
                128
        ));

        assertThat(response.systemError()).isTrue();
        assertThat(response.stderr()).contains("Unsupported language: ruby");
    }

    @Test
    void execute_returnsSystemErrorWhenExecutorThrows() {
        RunnerExecutionService service = new RunnerExecutionService(
                new RunnerLanguageExecutorSelector(java.util.List.of(new ThrowingLanguageExecutor("cpp")))
        );

        RunnerExecutionResponse response = service.execute(new RunnerExecutionRequest(
                11L,
                21L,
                "cpp",
                "int main() {}",
                "",
                1000,
                128
        ));

        assertThat(response.systemError()).isTrue();
        assertThat(response.stderr()).contains("docker unavailable");
    }

    private static final class RecordingLanguageExecutor implements LanguageExecutor {

        private final String language;
        private final JudgeExecutionResult result;
        private JudgeContext lastContext;

        private RecordingLanguageExecutor(String language, JudgeExecutionResult result) {
            this.language = language;
            this.result = result;
        }

        @Override
        public boolean supports(String language) {
            return this.language.equalsIgnoreCase(language);
        }

        @Override
        public JudgeExecutionResult execute(JudgeContext context) {
            this.lastContext = context;
            return result;
        }

        private JudgeContext lastContext() {
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
        public JudgeExecutionResult execute(JudgeContext context) {
            throw new IllegalStateException("docker unavailable");
        }
    }
}
