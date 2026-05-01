package com.example.ssoj.judge.executor;

import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class DockerProcessExecutorBatchOutputComparisonTest {

    private final DockerProcessExecutor dockerProcessExecutor = new DockerProcessExecutor();

    @Test
    void compareBatchOutputs_acceptsJavaOutputWithFinalNewline() throws Exception {
        Path workspace = workspace("java", "15", "15\n");

        JudgeRunResult result = compare("java", workspace);

        assertThat(result).isNull();
        deleteDirectory(workspace);
    }

    @Test
    void compareBatchOutputs_acceptsPythonOutputWithFinalNewline() throws Exception {
        Path workspace = workspace("python", "15", "15\n");

        JudgeRunResult result = compare("python", workspace);

        assertThat(result).isNull();
        deleteDirectory(workspace);
    }

    @Test
    void compareBatchOutputs_acceptsCppOutputWithFinalNewline() throws Exception {
        Path workspace = workspace("cpp", "15", "15\n");

        JudgeRunResult result = compare("cpp", workspace);

        assertThat(result).isNull();
        deleteDirectory(workspace);
    }

    @Test
    void compareBatchOutputs_acceptsExpectedFinalNewlineOnly() throws Exception {
        Path workspace = workspace("python", "15\n", "15");

        JudgeRunResult result = compare("python", workspace);

        assertThat(result).isNull();
        deleteDirectory(workspace);
    }

    @Test
    void compareBatchOutputs_acceptsExpectedTrailingWhitespace() throws Exception {
        Path workspace = workspace("python", "15  \n", "15");

        JudgeRunResult result = compare("python", workspace);

        assertThat(result).isNull();
        deleteDirectory(workspace);
    }

    @Test
    void compareBatchOutputs_acceptsExpectedTrailingBlankLines() throws Exception {
        Path workspace = workspace("python", "15\n\n", "15");

        JudgeRunResult result = compare("python", workspace);

        assertThat(result).isNull();
        deleteDirectory(workspace);
    }

    @Test
    void compareBatchOutputs_rejectsMissingInternalWhitespace() throws Exception {
        Path workspace = workspace("python", "1 2", "12");

        JudgeRunResult result = compare("python", workspace);

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.WA);
        assertThat(result.failedTestcaseOrder()).isEqualTo(3);
        deleteDirectory(workspace);
    }

    @Test
    void compareBatchOutputs_logsFirstHiddenCaseFileIndexAndTestcaseOrder(CapturedOutput output) throws Exception {
        Path workspace = workspace("python", "1 2", "12");

        JudgeRunResult result = compare("python", workspace);

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.WA);
        assertThat(result.failedTestcaseOrder()).isEqualTo(3);
        assertThat(output).contains("submissionId=57");
        assertThat(output).contains("language=python");
        assertThat(output).contains("fileIndex=1");
        assertThat(output).contains("testcaseOrder=3");
        assertThat(output).contains("expected=1 2");
        assertThat(output).contains("output=12");
        assertThat(output).contains("normalizedExpected=1 2");
        assertThat(output).contains("normalizedOutput=12");
        deleteDirectory(workspace);
    }

    private JudgeRunResult compare(String language, Path workspace) throws Exception {
        return dockerProcessExecutor.compareCompletedBatchOutputs(
                context(language),
                workspace,
                new JudgeRunResult(SubmissionResult.AC, 10, 100, null)
        );
    }

    private Path workspace(String language, String expected, String output) throws Exception {
        Path workspace = Files.createTempDirectory("batch-output-compare-" + language + "-");
        Files.writeString(workspace.resolve("input_1.txt"), "sample input\n", StandardCharsets.UTF_8);
        Files.writeString(workspace.resolve("expected_1.txt"), expected, StandardCharsets.UTF_8);
        Files.writeString(workspace.resolve("output_1.txt"), output, StandardCharsets.UTF_8);
        Files.writeString(workspace.resolve("order_1.txt"), "3", StandardCharsets.UTF_8);
        Files.writeString(
                workspace.resolve("usage_1.txt"),
                """
                \tElapsed (wall clock) time (h:mm:ss or m:ss): 0:00.01
                \tMaximum resident set size (kbytes): 100
                """,
                StandardCharsets.UTF_8
        );
        return workspace;
    }

    private JudgeRunContext context(String language) {
        return new JudgeRunContext(
                57L,
                1L,
                language,
                "",
                List.of(new HiddenTestCaseSnapshot(1L, 3, "sample input\n", "unused")),
                1000,
                128
        );
    }

    private void deleteDirectory(Path directory) throws Exception {
        if (!Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }
}
