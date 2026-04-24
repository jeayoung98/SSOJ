package com.example.ssoj.judge;

import com.example.ssoj.judge.application.port.ExecutionGateway;
import com.example.ssoj.judge.application.sevice.JudgePersistenceService;
import com.example.ssoj.judge.application.sevice.JudgeService;
import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.domain.model.StartedJudging;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeServiceTest {

    private static final Long SUBMISSION_11 = 11L;
    private static final Long SUBMISSION_12 = 12L;
    private static final Long SUBMISSION_13 = 13L;
    private static final Long SUBMISSION_14 = 14L;
    private static final Long SUBMISSION_15 = 15L;
    private static final Long SUBMISSION_16 = 16L;
    private static final Long SUBMISSION_17 = 17L;
    private static final Long SUBMISSION_18 = 18L;
    private static final Long SUBMISSION_19 = 19L;
    private static final Long SUBMISSION_20 = 20L;
    private static final Long SUBMISSION_21 = 21L;
    private static final Long CASE_201 = 201L;
    private static final Long CASE_202 = 202L;
    private static final Long CASE_203 = 203L;
    private static final Long CASE_204 = 204L;
    private static final Long CASE_205 = 205L;
    private static final Long CASE_206 = 206L;
    private static final Long CASE_207 = 207L;
    private static final Long CASE_301 = 301L;
    private static final Long CASE_302 = 302L;
    private static final Long CASE_303 = 303L;

    @Mock
    private JudgePersistenceService judgePersistenceService;

    @Mock
    private ExecutionGateway executionGateway;

    @Test
    void judge_stopsAtFirstFailure_andFinishesWithWa() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_11,
                "python",
                List.of(
                        hiddenTestCase(CASE_201, 1, "1 2", "3"),
                        hiddenTestCase(CASE_202, 2, "5 8", "13")
                )
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_11)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(true, "4\n", "", 0, 12, 64, false, false, false, false));

        judgeService.judge(SUBMISSION_11);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(executionGateway, times(1)).execute(any(JudgeContext.class));
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_11), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.WA);
        assertThat(runResult.failedTestcaseOrder()).isEqualTo(1);
        assertThat(runResult.executionTimeMs()).isEqualTo(12);
        assertThat(runResult.memoryKb()).isEqualTo(64);
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_mapsJavaCompileErrorToCe_andFinishes() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_12,
                "java",
                List.of(
                        hiddenTestCase(CASE_203, 1, "", "OK"),
                        hiddenTestCase(CASE_204, 2, "", "OK")
                )
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_12)).thenReturn(startedJudging);
        when(executionGateway.supports("java")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(false, "", "Main.java:1: error: ';' expected", 1, 5, 64, false, false, true, false));

        judgeService.judge(SUBMISSION_12);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_12), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.CE);
        assertThat(runResult.failedTestcaseOrder()).isNull();
        assertThat(runResult.executionTimeMs()).isEqualTo(5);
        assertThat(runResult.memoryKb()).isEqualTo(64);
        verify(executionGateway, times(1)).execute(any(JudgeContext.class));
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_marksSubmissionAsSystemErrorWhenExecutorThrows_andStillFinishes() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_13,
                "cpp",
                List.of(hiddenTestCase(CASE_204, "", "OK"))
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_13)).thenReturn(startedJudging);
        when(executionGateway.supports("cpp")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class))).thenThrow(new RuntimeException("docker failed"));

        judgeService.judge(SUBMISSION_13);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_13), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.SYSTEM_ERROR);
        assertThat(runResult.failedTestcaseOrder()).isNull();
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_skipsAlreadyCompletedSubmission_withoutFinishingAgain() {
        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_14)).thenReturn(null);

        judgeService.judge(SUBMISSION_14);

        verify(executionGateway, never()).supports(any());
        verify(executionGateway, never()).execute(any(JudgeContext.class));
        verify(judgePersistenceService, never()).saveResultsAndFinish(any(), any(), any());
    }

    @Test
    void judge_stopsAtTimeout_andFinishesWithTle() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_15,
                "cpp",
                List.of(
                        hiddenTestCase(CASE_205, 1, "", "OK"),
                        hiddenTestCase(CASE_206, 2, "", "OK")
                )
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_15)).thenReturn(startedJudging);
        when(executionGateway.supports("cpp")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(JudgeExecutionResult.timeout(1000));

        judgeService.judge(SUBMISSION_15);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_15), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.TLE);
        assertThat(runResult.failedTestcaseOrder()).isEqualTo(1);
        assertThat(runResult.executionTimeMs()).isEqualTo(1000);
        assertThat(runResult.memoryKb()).isNull();
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_stopsAtRuntimeError_andFinishesWithRe() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_21,
                "python",
                List.of(
                        hiddenTestCase(CASE_301, 1, "1", "1"),
                        hiddenTestCase(CASE_302, 2, "2", "2")
                )
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_21)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(false, "", "RuntimeError", 1, 9, 96, false, false, false, false));

        judgeService.judge(SUBMISSION_21);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        verify(executionGateway, times(1)).execute(any(JudgeContext.class));
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_21), runResultCaptor.capture(), any(Instant.class));

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.RE);
        assertThat(runResult.failedTestcaseOrder()).isEqualTo(1);
        assertThat(runResult.executionTimeMs()).isEqualTo(9);
        assertThat(runResult.memoryKb()).isEqualTo(96);
    }

    @Test
    void judge_finishesWithSystemErrorWhenLanguageIsUnsupported() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_16,
                "unknown",
                List.of(hiddenTestCase(CASE_206, "", "OK"))
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_16)).thenReturn(startedJudging);
        when(executionGateway.supports("unknown")).thenReturn(false);

        judgeService.judge(SUBMISSION_16);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(executionGateway, never()).execute(any(JudgeContext.class));
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_16), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.SYSTEM_ERROR);
        assertThat(runResult.failedTestcaseOrder()).isNull();
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_keepsAcWhenSingleHiddenCaseSucceeds() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_17,
                "python",
                List.of(hiddenTestCase(CASE_207, "", "OK"))
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_17)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(true, "OK\n", "", 0, 2400, 64, false, false, false, false));

        judgeService.judge(SUBMISSION_17);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_17), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.AC);
        assertThat(runResult.failedTestcaseOrder()).isNull();
        assertThat(runResult.executionTimeMs()).isEqualTo(2400);
        assertThat(runResult.memoryKb()).isEqualTo(64);
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_doesNotExecuteHiddenCasesAfterFirstFailure() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_18,
                "python",
                List.of(
                        hiddenTestCase(CASE_301, 1, "1", "1"),
                        hiddenTestCase(CASE_302, 2, "2", "2"),
                        hiddenTestCase(CASE_303, 3, "3", "3")
                )
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_18)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(true, "1\n", "", 0, 3, 32, false, false, false, false))
                .thenReturn(new JudgeExecutionResult(true, "wrong\n", "", 0, 4, 32, false, false, false, false));

        judgeService.judge(SUBMISSION_18);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        verify(executionGateway, times(2)).execute(any(JudgeContext.class));
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_18), runResultCaptor.capture(), any(Instant.class));

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.WA);
        assertThat(runResult.failedTestcaseOrder()).isEqualTo(2);
        assertThat(runResult.executionTimeMs()).isEqualTo(4);
        assertThat(runResult.memoryKb()).isEqualTo(32);
    }

    @Test
    void judge_keepsAcAfterAllHiddenCases_andAggregatesMaxExecutionTimeAndMemory() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_19,
                "python",
                List.of(
                        hiddenTestCase(CASE_301, 1, "1", "1"),
                        hiddenTestCase(CASE_302, 2, "2", "2"),
                        hiddenTestCase(CASE_303, 3, "3", "3")
                )
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_19)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(true, "1\n", "", 0, 10, 100, false, false, false, false))
                .thenReturn(new JudgeExecutionResult(true, "2\n", "", 0, 25, 64, false, false, false, false))
                .thenReturn(new JudgeExecutionResult(true, "3\n", "", 0, 7, 512, false, false, false, false));

        judgeService.judge(SUBMISSION_19);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        verify(executionGateway, times(3)).execute(any(JudgeContext.class));
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_19), runResultCaptor.capture(), any(Instant.class));

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.AC);
        assertThat(runResult.failedTestcaseOrder()).isNull();
        assertThat(runResult.executionTimeMs()).isEqualTo(25);
        assertThat(runResult.memoryKb()).isEqualTo(512);
    }

    @Test
    void judge_stopsWithMleWhenMemoryUsageExceedsProblemLimit() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_20,
                "python",
                List.of(
                        hiddenTestCase(CASE_301, 1, "1", "1"),
                        hiddenTestCase(CASE_302, 2, "2", "2")
                )
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_20)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(true, "1\n", "", 0, 8, 128 * 1024 + 1, false, false, false, false));

        judgeService.judge(SUBMISSION_20);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        verify(executionGateway, times(1)).execute(any(JudgeContext.class));
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_20), runResultCaptor.capture(), any(Instant.class));

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.MLE);
        assertThat(runResult.failedTestcaseOrder()).isEqualTo(1);
        assertThat(runResult.executionTimeMs()).isEqualTo(8);
        assertThat(runResult.memoryKb()).isEqualTo(128 * 1024 + 1);
    }

    @Test
    void judge_stopsWithMleWhenExecutionReportsContainerOom() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_20,
                "python",
                List.of(hiddenTestCase(CASE_301, 1, "1", "1"))
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_20)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(false, "", "Killed", 137, 20, 0, false, false, false, true));

        judgeService.judge(SUBMISSION_20);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_20), runResultCaptor.capture(), any(Instant.class));

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.MLE);
        assertThat(runResult.failedTestcaseOrder()).isEqualTo(1);
        assertThat(runResult.executionTimeMs()).isEqualTo(20);
    }

    private static StartedJudging startedJudging(
            Long submissionId,
            String language,
            List<HiddenTestCaseSnapshot> hiddenTestCases
    ) {
        return new StartedJudging(
                submissionId,
                1L,
                language,
                "print('hello')",
                1000,
                128,
                hiddenTestCases
        );
    }

    private static HiddenTestCaseSnapshot hiddenTestCase(Long testCaseId, String input, String expectedOutput) {
        return new HiddenTestCaseSnapshot(testCaseId, input, expectedOutput);
    }

    private static HiddenTestCaseSnapshot hiddenTestCase(Long testCaseId, int order, String input, String expectedOutput) {
        return new HiddenTestCaseSnapshot(testCaseId, order, input, expectedOutput);
    }
}
