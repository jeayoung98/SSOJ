package com.example.ssoj.judge.executor;

import com.example.ssoj.judge.application.port.JudgeProgressPublisher;
import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeProgressEvent;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DockerProcessExecutorProgressTest {

    @Test
    void buildRunAllScript_writesProgressAfterEachExecutedTestcase() {
        DockerProcessExecutor executor = new DockerProcessExecutor();

        String script = executor.buildRunAllScript(
                new JudgeRunContext(
                        220L,
                        1L,
                        "python",
                        "print(1)",
                        List.of(
                                new HiddenTestCaseSnapshot(1L, 1, "", ""),
                                new HiddenTestCaseSnapshot(2L, 2, "", "")
                        ),
                        1000,
                        128
                ),
                null,
                null,
                "python3 main.py"
        );

        assertThat(script).contains("write_progress()");
        assertThat(script).contains(">> progress.jsonl 2>/dev/null || true");
        assertThat(script).contains("write_progress \"$i\"");
    }

    @Test
    void parseProgressLine_addsSubmissionIdWithoutSensitiveFields() throws Exception {
        DockerProcessExecutor executor = new DockerProcessExecutor();

        JudgeProgressEvent event = executor.parseProgressLine(
                220L,
                "{\"phase\":\"RUNNING\",\"completedTestcases\":37,\"totalTestcases\":100,\"progressPercent\":37}"
        );

        assertThat(event.submissionId()).isEqualTo(220L);
        assertThat(event.phase()).isEqualTo("RUNNING");
        assertThat(event.completedTestcases()).isEqualTo(37);
        assertThat(event.totalTestcases()).isEqualTo(100);
        assertThat(event.progressPercent()).isEqualTo(37);
        assertThat(event.result()).isNull();
    }

    @Test
    void progressPublisherFailure_doesNotEscape() {
        DockerProcessExecutor executor = new DockerProcessExecutor(new ThrowingProgressPublisher());

        executor.publishProgress(220L, "{\"phase\":\"RUNNING\",\"completedTestcases\":1,\"totalTestcases\":2,\"progressPercent\":50}");
    }

    private static class ThrowingProgressPublisher implements JudgeProgressPublisher {

        @Override
        public void publish(JudgeProgressEvent event) {
            throw new IllegalStateException("publish failed");
        }
    }
}
