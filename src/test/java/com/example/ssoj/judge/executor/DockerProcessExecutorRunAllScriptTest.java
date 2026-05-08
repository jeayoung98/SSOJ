package com.example.ssoj.judge.executor;

import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DockerProcessExecutorRunAllScriptTest {

    private final DockerProcessExecutor dockerProcessExecutor = new DockerProcessExecutor();

    @Test
    void buildRunAllScript_comparesNormalizedOutputAfterSuccessfulRunAndStopsAtWrongAnswer() {
        String script = dockerProcessExecutor.buildRunAllScript(
                context(),
                null,
                null,
                "python3 main.py"
        );

        assertThat(script).contains("normalize_file_for_compare()");
        assertThat(script).contains("gsub(/\\r\\n/, \"\\n\", text)");
        assertThat(script).contains("gsub(/\\r/, \"\\n\", text)");
        assertThat(script).contains("sub(/[[:space:]]+$/, \"\", line)");
        assertThat(script).contains("normalize_file_for_compare \"expected_$i.txt\" \"$expected_normalized_file\"");
        assertThat(script).contains("normalize_file_for_compare \"$output_file\" \"$output_normalized_file\"");
        assertThat(script).contains("if ! cmp -s \"$expected_normalized_file\" \"$output_normalized_file\"; then");
        assertThat(script).contains("write_result WA \"$order\" \"$max_time\" \"$max_mem\" \"$i\"");
        assertThat(script).contains("exit 0");
    }

    @Test
    void buildRunAllScript_recordsProgressBeforeWrongAnswerResult() {
        String script = dockerProcessExecutor.buildRunAllScript(
                context(),
                null,
                null,
                "python3 main.py"
        );

        int progressIndex = script.indexOf("write_progress \"$i\"");
        int timeoutIndex = script.indexOf("write_result TLE \"$order\" \"$max_time\" \"$max_mem\" \"$i\"");
        int memoryByExitIndex = script.indexOf("if [ \"$run_exit\" -eq 137 ]; then");
        int memoryByUsageIndex = script.indexOf("if [ -n \"$parsed_mem\" ] && [ \"$parsed_mem\" -gt \"$MEMORY_LIMIT_KB\" ]; then");
        int runtimeErrorIndex = script.indexOf("if [ \"$run_exit\" -ne 0 ]; then");
        int compareIndex = script.indexOf("normalize_file_for_compare \"expected_$i.txt\" \"$expected_normalized_file\"");
        int wrongAnswerIndex = script.indexOf("write_result WA \"$order\" \"$max_time\" \"$max_mem\" \"$i\"");
        int incrementIndex = script.indexOf("i=$((i + 1))");

        assertThat(progressIndex).isLessThan(timeoutIndex);
        assertThat(timeoutIndex).isLessThan(memoryByExitIndex);
        assertThat(memoryByExitIndex).isLessThan(memoryByUsageIndex);
        assertThat(memoryByUsageIndex).isLessThan(runtimeErrorIndex);
        assertThat(runtimeErrorIndex).isLessThan(compareIndex);
        assertThat(compareIndex).isLessThan(wrongAnswerIndex);
        assertThat(wrongAnswerIndex).isLessThan(incrementIndex);
    }

    @Test
    void buildRunAllScript_measuresElapsedTimeInBashAndStoresPerTestcaseTime() {
        String script = dockerProcessExecutor.buildRunAllScript(
                context(),
                null,
                null,
                "python3 main.py"
        );

        assertThat(script).contains("now_ms()");
        assertThat(script).contains("fallback=\"$(date +%s%3N 2>/dev/null)\"");
        assertThat(script).contains("started_at_ms=\"$(now_ms)\"");
        assertThat(script).contains("finished_at_ms=\"$(now_ms)\"");
        assertThat(script).contains("measured_time_ms=$((finished_at_ms - started_at_ms))");
        assertThat(script).contains("parsed_time=\"$measured_time_ms\"");
        assertThat(script).contains("time_file=\"time_$i.txt\"");
        assertThat(script).contains("printf '%s\\n' \"$parsed_time\" > \"$time_file\"");
    }

    private JudgeRunContext context() {
        return new JudgeRunContext(
                57L,
                1L,
                "python",
                "",
                List.of(
                        new HiddenTestCaseSnapshot(1L, 3, "ok\n", "ok\n"),
                        new HiddenTestCaseSnapshot(2L, 7, "actual\n", "expected\n")
                ),
                1000,
                128
        );
    }
}
