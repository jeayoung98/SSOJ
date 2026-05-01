package com.example.ssoj.judge.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

@Component
public class DockerProcessExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerProcessExecutor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CPU_LIMIT = "1";
    private static final long COMMAND_TIMEOUT_BUFFER_MS = 5000L;
    private static final Pattern ELAPSED_TIME_PATTERN = Pattern.compile(
            "Elapsed \\(wall clock\\) time \\(h:mm:ss or m:ss\\):\\s*(.+)"
    );
    private static final Pattern MEMORY_PATTERN = Pattern.compile(
            "Maximum resident set size \\(kbytes\\):\\s*(\\d+)"
    );

    public JudgeExecutionResult executeCompile(
            JudgeContext context,
            Path workspaceDirectory,
            String dockerImage,
            int dockerMemoryMb,
            String compileCommand,
            long compileTimeoutMs
    )
            throws IOException, InterruptedException {
        CommandResult result = runInContainer(
                context,
                workspaceDirectory,
                dockerImage,
                dockerMemoryMb,
                compileCommand,
                compileTimeoutMs + COMMAND_TIMEOUT_BUFFER_MS,
                null
        );

        if (result.systemError()) {
            return JudgeExecutionResult.systemError(result.stderr());
        }

        if (result.exitCode() != 0) {
            return JudgeExecutionResult.compilationError(result.stderr(), result.exitCode());
        }

        return new JudgeExecutionResult(true, result.stdout(), result.stderr(), 0, null, null, false, false, false, false);
    }

    public JudgeExecutionResult executeRun(
            JudgeContext context,
            Path workspaceDirectory,
            String dockerImage,
            int dockerMemoryMb,
            String runCommand
    ) throws IOException, InterruptedException {
        Path inputFile = workspaceDirectory.resolve("input.txt");
        Path outputFile = workspaceDirectory.resolve("output.txt");
        Path errorFile = workspaceDirectory.resolve("error.txt");
        Path usageFile = workspaceDirectory.resolve("usage.txt");

        Files.writeString(inputFile, context.input() == null ? "" : context.input(), StandardCharsets.UTF_8);
        Files.deleteIfExists(outputFile);
        Files.deleteIfExists(errorFile);
        Files.deleteIfExists(usageFile);

        String wrappedRunCommand = buildMeasuredRunCommand(runCommand, context.timeLimitMs());
        CommandResult result = runInContainer(
                context,
                workspaceDirectory,
                dockerImage,
                dockerMemoryMb,
                wrappedRunCommand,
                context.timeLimitMs() + COMMAND_TIMEOUT_BUFFER_MS,
                null
        );

        if (result.systemError()) {
            return JudgeExecutionResult.systemError(result.stderr());
        }

        String stdout = readWorkspaceFile(outputFile);
        String stderr = combineErrors(readWorkspaceFile(errorFile), result.stderr());
        UsageMetrics usageMetrics = parseUsageMetrics(usageFile, stderr, result.exitCode(), context.timeLimitMs());
        if (usageMetrics == null) {
            return JudgeExecutionResult.systemError("Failed to parse /usr/bin/time -v output for submission " + context.submissionId());
        }

        boolean timedOut = result.exitCode() == 124;
        boolean memoryLimitExceeded = result.exitCode() == 137 || containsDockerOom(stderr);
        return new JudgeExecutionResult(
                result.exitCode() == 0,
                stdout,
                stderr,
                result.exitCode(),
                usageMetrics.executionTimeMs(),
                usageMetrics.memoryUsageKb(),
                false,
                timedOut,
                false,
                memoryLimitExceeded
        );
    }

    public JudgeRunResult executeBatch(
            JudgeRunContext context,
            Path workspaceDirectory,
            String dockerImage,
            int dockerMemoryMb,
            String compileCommand,
            Long compileTimeoutMs,
            String runCommand
    ) throws IOException, InterruptedException {
        writeBatchTestCaseFiles(context, workspaceDirectory);

        Path resultFile = workspaceDirectory.resolve("result.json");
        Path scriptFile = workspaceDirectory.resolve("run_all.sh");
        Files.deleteIfExists(resultFile);
        Files.writeString(
                scriptFile,
                buildRunAllScript(context, compileCommand, compileTimeoutMs, runCommand),
                StandardCharsets.UTF_8
        );

        long waitTimeoutMs = resolveBatchWaitTimeoutMs(context, compileTimeoutMs);
        CommandResult result = runInContainer(
                new JudgeContext(
                        context.submissionId(),
                        context.problemId(),
                        context.language(),
                        context.sourceCode(),
                        "",
                        context.timeLimitMs(),
                        context.memoryLimitMb()
                ),
                workspaceDirectory,
                dockerImage,
                dockerMemoryMb,
                "/usr/bin/bash /workspace/run_all.sh",
                waitTimeoutMs,
                null
        );

        if (result.systemError()) {
            logBatchFailureDiagnostics(context.submissionId(), workspaceDirectory, result);
            return JudgeRunResult.systemError();
        }

        if (result.exitCode() == null || result.exitCode() != 0) {
            logBatchFailureDiagnostics(context.submissionId(), workspaceDirectory, result);
            return JudgeRunResult.systemError();
        }

        if (!Files.exists(resultFile)) {
            logBatchFailureDiagnostics(context.submissionId(), workspaceDirectory, result);
            return JudgeRunResult.systemError();
        }

        BatchResult batchResult;
        try {
            batchResult = objectMapper.readValue(resultFile.toFile(), BatchResult.class);
        } catch (Exception exception) {
            logBatchFailureDiagnostics(context.submissionId(), workspaceDirectory, result);
            log.error("Failed to parse batch result.json for submission {}", context.submissionId(), exception);
            return JudgeRunResult.systemError();
        }
        if (batchResult.result() == null) {
            logBatchFailureDiagnostics(context.submissionId(), workspaceDirectory, result);
            return JudgeRunResult.systemError();
        }
        if (batchResult.result() == SubmissionResult.WA) {
            logWrongAnswerDetails(context.submissionId(), workspaceDirectory, batchResult);
        }

        return new JudgeRunResult(
                batchResult.result(),
                batchResult.executionTimeMs(),
                batchResult.memoryUsageKb(),
                batchResult.failedTestcaseOrder()
        );
    }

    private CommandResult runInContainer(
            JudgeContext context,
            Path workspaceDirectory,
            String dockerImage,
            int dockerMemoryMb,
            String containerCommand,
            long waitTimeoutMs,
            String stdin
    ) throws IOException, InterruptedException {
        Path cidFile = workspaceDirectory.resolve(".container.cid");
        List<String> command = List.of(
                "docker",
                "run",
                "--rm",
                "-i",
                "--cidfile",
                cidFile.toAbsolutePath().toString(),
                "--network",
                "none",
                "-m",
                dockerMemoryMb + "m",
                "--cpus",
                CPU_LIMIT,
                "-v",
                workspaceDirectory.toAbsolutePath() + ":/workspace",
                "-w",
                "/workspace",
                dockerImage,
                "sh",
                "-lc",
                containerCommand
        );

        Process process = null;
        try {
            log.info(
                    "Starting Docker execution for submission {} with image={} command={}",
                    context.submissionId(),
                    dockerImage,
                    containerCommand
            );
            process = new ProcessBuilder(command).start();

            try (OutputStream outputStream = process.getOutputStream()) {
                if (stdin != null) {
                    outputStream.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            }

            boolean finished = process.waitFor(waitTimeoutMs, TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.info(
                        "Docker client process timed out for submission {} after {} ms with image={}",
                        context.submissionId(),
                        waitTimeoutMs,
                        dockerImage
                );
                return new CommandResult("", "Docker client process timed out", null, true);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();

            log.info(
                    "Docker execution finished for submission {} with image={} exitCode={} memoryLimit={}m cpuLimit={}",
                    context.submissionId(),
                    dockerImage,
                    exitCode,
                    dockerMemoryMb,
                    CPU_LIMIT
            );
            return new CommandResult(stdout, stderr, exitCode, false);
        } catch (IOException exception) {
            log.warn(
                    "Failed to start Docker for submission {} with image={} command={}",
                    context.submissionId(),
                    dockerImage,
                    containerCommand,
                    exception
            );
            throw exception;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                log.info("Destroyed running Docker client process for submission {}", context.submissionId());
            }
            cleanupContainer(context.submissionId(), cidFile);
        }
    }

    private String buildMeasuredRunCommand(String runCommand, Integer timeLimitMs) {
        String timeoutSeconds = toTimeoutSeconds(timeLimitMs);
        return "/usr/bin/time -v -o /workspace/usage.txt timeout "
                + timeoutSeconds
                + "s "
                + runCommand
                + " < /workspace/input.txt > /workspace/output.txt 2> /workspace/error.txt";
    }

    private void writeBatchTestCaseFiles(JudgeRunContext context, Path workspaceDirectory) throws IOException {
        for (int index = 0; index < context.hiddenTestCases().size(); index++) {
            HiddenTestCaseSnapshot testCase = context.hiddenTestCases().get(index);
            int fileIndex = index + 1;
            Files.writeString(
                    workspaceDirectory.resolve("input_" + fileIndex + ".txt"),
                    testCase.input() == null ? "" : testCase.input(),
                    StandardCharsets.UTF_8
            );
            Files.writeString(
                    workspaceDirectory.resolve("expected_" + fileIndex + ".txt"),
                    testCase.expectedOutput() == null ? "" : testCase.expectedOutput(),
                    StandardCharsets.UTF_8
            );
            Files.writeString(
                    workspaceDirectory.resolve("order_" + fileIndex + ".txt"),
                    String.valueOf(testCase.testCaseOrder()),
                    StandardCharsets.UTF_8
            );
        }
    }

    private String buildRunAllScript(
            JudgeRunContext context,
            String compileCommand,
            Long compileTimeoutMs,
            String runCommand
    ) {
        String timeoutSeconds = toTimeoutSeconds(context.timeLimitMs());
        String compileBlock = "";
        if (compileCommand != null && !compileCommand.isBlank()) {
            compileBlock = """
                    timeout "$COMPILE_TIMEOUT_SECONDS"s /usr/bin/bash -lc "$COMPILE_COMMAND" > compile_stdout.txt 2> compile_stderr.txt
                    compile_exit=$?
                    if [ "$compile_exit" -ne 0 ]; then
                      write_result CE "" "" "" ""
                      exit 0
                    fi

                    """;
        }

        return """
                #!/usr/bin/env bash
                set +e

                TEST_COUNT=%d
                TIME_LIMIT_SECONDS=%s
                MEMORY_LIMIT_KB=%d
                COMPILE_TIMEOUT_SECONDS=%s
                COMPILE_COMMAND=%s
                RUN_COMMAND=%s
                max_time=""
                max_mem=""

                json_number_or_null() {
                  if [ -z "$1" ]; then
                    printf 'null'
                  else
                    printf '%%s' "$1"
                  fi
                }

                write_result() {
                  result="$1"
                  failed_order="$2"
                  execution_time="$3"
                  memory_usage="$4"
                  failed_file_index="$5"
                  printf '{"result":"%%s","executionTimeMs":%%s,"memoryUsageKb":%%s,"failedTestcaseOrder":%%s,"failedFileIndex":%%s}\\n' \\
                    "$result" \\
                    "$(json_number_or_null "$execution_time")" \\
                    "$(json_number_or_null "$memory_usage")" \\
                    "$(json_number_or_null "$failed_order")" \\
                    "$(json_number_or_null "$failed_file_index")" > result.json
                }

                update_max() {
                  time_value="$1"
                  mem_value="$2"
                  if [ -n "$time_value" ] && { [ -z "$max_time" ] || [ "$time_value" -gt "$max_time" ]; }; then
                    max_time="$time_value"
                  fi
                  if [ -n "$mem_value" ] && { [ -z "$max_mem" ] || [ "$mem_value" -gt "$max_mem" ]; }; then
                    max_mem="$mem_value"
                  fi
                }

                elapsed_to_ms() {
                  awk -v value="$1" 'BEGIN {
                    n = split(value, parts, ":")
                    if (n == 2) {
                      total = (parts[1] * 60) + parts[2]
                    } else if (n == 3) {
                      total = (parts[1] * 3600) + (parts[2] * 60) + parts[3]
                    } else {
                      total = 0
                    }
                    printf "%%d", (total * 1000) + 0.5
                  }'
                }

                parse_usage() {
                  usage_file="$1"
                  if [ ! -f "$usage_file" ]; then
                    parsed_time=""
                    parsed_mem=""
                    return
                  fi
                  elapsed="$(grep 'Elapsed (wall clock)' "$usage_file" | sed 's/^.*):[[:space:]]*//')"
                  parsed_time="$(elapsed_to_ms "$elapsed")"
                  parsed_mem="$(grep 'Maximum resident set size' "$usage_file" | sed 's/^.*:[[:space:]]*//')"
                }

                normalize_output() {
                  source_file="$1"
                  target_file="$2"
                  if [ ! -f "$source_file" ]; then
                    : > "$target_file"
                    return
                  fi
                  awk '
                    {
                      sub(/\\r$/, "")
                      sub(/[[:space:]]+$/, "")
                      lines[++count] = $0
                    }
                    END {
                      while (count > 0 && lines[count] == "") {
                        count--
                      }
                      for (i = 1; i <= count; i++) {
                        if (i > 1) {
                          printf "\\n"
                        }
                        printf "%%s", lines[i]
                      }
                    }
                  ' "$source_file" > "$target_file"
                }

                %s
                i=1
                while [ "$i" -le "$TEST_COUNT" ]; do
                  order="$(cat "order_$i.txt")"
                  usage_file="usage_$i.txt"
                  output_file="output_$i.txt"
                  error_file="error_$i.txt"
                  rm -f "$usage_file" "$output_file" "$error_file" actual_normalized.txt expected_normalized.txt

                  /usr/bin/time -v -o "$usage_file" timeout "$TIME_LIMIT_SECONDS"s /usr/bin/bash -lc "$RUN_COMMAND" \\
                    < "input_$i.txt" > "$output_file" 2> "$error_file"
                  run_exit=$?
                  parse_usage "$usage_file"
                  if [ "$run_exit" -eq 124 ] && [ -z "$parsed_time" ]; then
                    parsed_time=%d
                  fi
                  update_max "$parsed_time" "$parsed_mem"

                  if [ "$run_exit" -eq 124 ]; then
                    write_result TLE "$order" "$max_time" "$max_mem" "$i"
                    exit 0
                  fi
                  if [ "$run_exit" -eq 137 ]; then
                    write_result MLE "$order" "$max_time" "$max_mem" "$i"
                    exit 0
                  fi
                  if [ -n "$parsed_mem" ] && [ "$parsed_mem" -gt "$MEMORY_LIMIT_KB" ]; then
                    write_result MLE "$order" "$max_time" "$max_mem" "$i"
                    exit 0
                  fi
                  if [ "$run_exit" -ne 0 ]; then
                    write_result RE "$order" "$max_time" "$max_mem" "$i"
                    exit 0
                  fi

                  normalize_output "$output_file" actual_normalized.txt
                  normalize_output "expected_$i.txt" expected_normalized.txt
                  if ! cmp -s actual_normalized.txt expected_normalized.txt; then
                    write_result WA "$order" "$max_time" "$max_mem" "$i"
                    exit 0
                  fi

                  i=$((i + 1))
                done

                write_result AC "" "$max_time" "$max_mem" ""
                exit 0
                """.formatted(
                context.hiddenTestCases().size(),
                timeoutSeconds,
                context.memoryLimitMb() == null ? 0 : context.memoryLimitMb() * 1024,
                toTimeoutSeconds(compileTimeoutMs == null ? 0 : compileTimeoutMs.intValue()),
                shellSingleQuote(compileCommand == null ? "" : compileCommand),
                shellSingleQuote(runCommand),
                compileBlock,
                context.timeLimitMs() == null ? 0 : context.timeLimitMs()
        );
    }

    private long resolveBatchWaitTimeoutMs(JudgeRunContext context, Long compileTimeoutMs) {
        long perCaseTimeoutMs = (context.timeLimitMs() == null ? 0L : context.timeLimitMs()) + COMMAND_TIMEOUT_BUFFER_MS;
        long compileMs = compileTimeoutMs == null ? 0L : compileTimeoutMs;
        return compileMs + (perCaseTimeoutMs * context.hiddenTestCases().size()) + COMMAND_TIMEOUT_BUFFER_MS;
    }

    private String shellSingleQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void logBatchFailureDiagnostics(Long submissionId, Path workspaceDirectory, CommandResult result) {
        try {
            log.error(
                    "Batch Docker execution failed. submissionId={} exitCode={} systemError={} dockerStdout={} dockerStderr={}",
                    submissionId,
                    result.exitCode(),
                    result.systemError(),
                    abbreviate(result.stdout()),
                    abbreviate(result.stderr())
            );
            log.error("Batch run_all.sh. submissionId={} content={}", submissionId, readWorkspaceFile(workspaceDirectory.resolve("run_all.sh")));
            log.error("Batch workspace files. submissionId={} files={}", submissionId, listWorkspaceFiles(workspaceDirectory));

            Path resultFile = workspaceDirectory.resolve("result.json");
            log.error(
                    "Batch result.json. submissionId={} exists={} content={}",
                    submissionId,
                    Files.exists(resultFile),
                    Files.exists(resultFile) ? readWorkspaceFile(resultFile) : ""
            );
            logBatchErrorFiles(submissionId, workspaceDirectory);
        } catch (Exception exception) {
            log.error("Failed to log batch diagnostics for submission {}", submissionId, exception);
        }
    }

    private void logWrongAnswerDetails(Long submissionId, Path workspaceDirectory, BatchResult batchResult) {
        Integer fileIndex = batchResult.failedFileIndex();
        Integer testcaseOrder = batchResult.failedTestcaseOrder();
        if (fileIndex == null) {
            log.error(
                    "Wrong answer occurred but failedFileIndex is missing. submissionId={} testcaseOrder={}",
                    submissionId,
                    testcaseOrder
            );
            return;
        }

        try {
            log.error(
                    "Wrong answer details. submissionId={} fileIndex={} testcaseOrder={} input={} expected={} output={} normalizedExpected={} normalizedOutput={}",
                    submissionId,
                    fileIndex,
                    testcaseOrder,
                    abbreviate(readWorkspaceFile(workspaceDirectory.resolve("input_" + fileIndex + ".txt"))),
                    abbreviate(readWorkspaceFile(workspaceDirectory.resolve("expected_" + fileIndex + ".txt"))),
                    abbreviate(readWorkspaceFile(workspaceDirectory.resolve("output_" + fileIndex + ".txt"))),
                    abbreviate(readWorkspaceFile(workspaceDirectory.resolve("expected_normalized.txt"))),
                    abbreviate(readWorkspaceFile(workspaceDirectory.resolve("actual_normalized.txt")))
            );
        } catch (Exception exception) {
            log.error(
                    "Failed to log wrong answer details. submissionId={} fileIndex={} testcaseOrder={}",
                    submissionId,
                    fileIndex,
                    testcaseOrder,
                    exception
            );
        }
    }

    private List<String> listWorkspaceFiles(Path workspaceDirectory) throws IOException {
        if (!Files.exists(workspaceDirectory)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(workspaceDirectory)) {
            return paths
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    private void logBatchErrorFiles(Long submissionId, Path workspaceDirectory) throws IOException {
        if (!Files.exists(workspaceDirectory)) {
            return;
        }

        try (Stream<Path> paths = Files.list(workspaceDirectory)) {
            List<Path> errorFiles = paths
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.equals("error.txt")
                                || fileName.equals("compile_stderr.txt")
                                || (fileName.startsWith("error_") && fileName.endsWith(".txt"));
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());

            for (Path errorFile : errorFiles) {
                log.error(
                        "Batch stderr file. submissionId={} file={} content={}",
                        submissionId,
                        errorFile.getFileName(),
                        abbreviate(readWorkspaceFile(errorFile))
                );
            }
        }
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        int maxLength = 20000;
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...[truncated]";
    }

    private String toTimeoutSeconds(Integer timeLimitMs) {
        double seconds = Math.max(0.001d, timeLimitMs / 1000.0d);
        return String.format(Locale.US, "%.3f", seconds);
    }

    private UsageMetrics parseUsageMetrics(Path usageFile, String stderr, Integer exitCode, Integer timeLimitMs) throws IOException {
        if (!Files.exists(usageFile)) {
            if (Integer.valueOf(124).equals(exitCode)) {
                return new UsageMetrics(timeLimitMs, 0);
            }
            if (Integer.valueOf(137).equals(exitCode) || containsDockerOom(stderr)) {
                return new UsageMetrics(0, 0);
            }
            return null;
        }

        String usageContent = Files.readString(usageFile, StandardCharsets.UTF_8);
        Matcher elapsedMatcher = ELAPSED_TIME_PATTERN.matcher(usageContent);
        Matcher memoryMatcher = MEMORY_PATTERN.matcher(usageContent);
        if (!elapsedMatcher.find() || !memoryMatcher.find()) {
            return null;
        }

        Integer executionTimeMs = parseElapsedTimeToMillis(elapsedMatcher.group(1).trim());
        Integer memoryKb = Integer.parseInt(memoryMatcher.group(1));
        if (executionTimeMs == null || memoryKb < 0) {
            return null;
        }

        return new UsageMetrics(executionTimeMs, memoryKb);
    }

    private Integer parseElapsedTimeToMillis(String value) {
        String[] parts = value.split(":");
        if (parts.length < 2 || parts.length > 3) {
            return null;
        }

        try {
            double secondsPart = Double.parseDouble(parts[parts.length - 1]);
            int minutesPart = Integer.parseInt(parts[parts.length - 2]);
            int hoursPart = parts.length == 3 ? Integer.parseInt(parts[0]) : 0;
            double totalSeconds = secondsPart + (minutesPart * 60L) + (hoursPart * 3600L);
            return (int) Math.round(totalSeconds * 1000.0d);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String readWorkspaceFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            return "";
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private String combineErrors(String workspaceError, String dockerError) {
        if (workspaceError == null || workspaceError.isBlank()) {
            return dockerError == null ? "" : dockerError;
        }
        if (dockerError == null || dockerError.isBlank()) {
            return workspaceError;
        }
        return workspaceError + System.lineSeparator() + dockerError;
    }

    public String normalizeOutputForComparison(String output) {
        if (output == null || output.isEmpty()) {
            return "";
        }

        String normalizedNewlines = output.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalizedNewlines.split("\n", -1);
        int endExclusive = lines.length;
        while (endExclusive > 0 && rstrip(lines[endExclusive - 1]).isEmpty()) {
            endExclusive--;
        }

        return Stream.of(lines)
                .limit(endExclusive)
                .map(this::rstrip)
                .collect(Collectors.joining("\n"));
    }

    private String rstrip(String value) {
        int index = value.length();
        while (index > 0 && Character.isWhitespace(value.charAt(index - 1))) {
            index--;
        }
        return value.substring(0, index);
    }

    private boolean containsDockerOom(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return false;
        }

        String normalized = stderr.toLowerCase(Locale.ROOT);
        return normalized.contains("oom")
                || normalized.contains("out of memory")
                || normalized.contains("killed");
    }

    private void cleanupContainer(Long submissionId, Path cidFile) {
        if (!Files.exists(cidFile)) {
            return;
        }

        try {
            String containerId = Files.readString(cidFile, StandardCharsets.UTF_8).trim();
            if (containerId.isBlank()) {
                return;
            }

            Process cleanupProcess = new ProcessBuilder("docker", "rm", "-f", containerId).start();
            boolean finished = cleanupProcess.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                cleanupProcess.destroyForcibly();
                log.warn("Timed out while cleaning Docker container {} for submission {}", containerId, submissionId);
                return;
            }

            if (cleanupProcess.exitValue() == 0) {
                log.info("Cleaned Docker container {} for submission {}", containerId, submissionId);
                return;
            }

            String stderr = new String(cleanupProcess.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!stderr.contains("No such container")) {
                log.warn("Failed to clean Docker container {} for submission {}: {}", containerId, submissionId, stderr.trim());
            }
        } catch (Exception exception) {
            log.warn("Cleanup failed for Docker container cid file {} of submission {}", cidFile, submissionId, exception);
        } finally {
            try {
                Files.deleteIfExists(cidFile);
            } catch (IOException exception) {
                log.warn("Failed to delete cid file {} for submission {}", cidFile, submissionId, exception);
            }
        }
    }

    private record CommandResult(
            String stdout,
            String stderr,
            Integer exitCode,
            boolean systemError
    ) {
    }

    private record UsageMetrics(
            Integer executionTimeMs,
            Integer memoryUsageKb
    ) {
    }

    private record BatchResult(
            SubmissionResult result,
            Integer executionTimeMs,
            Integer memoryUsageKb,
            Integer failedTestcaseOrder,
            Integer failedFileIndex
    ) {
    }
}
