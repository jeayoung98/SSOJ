package com.example.ssoj.judge;

import com.example.ssoj.judge.application.port.ExecutionGateway;
import com.example.ssoj.judge.application.sevice.JudgePersistenceService;
import com.example.ssoj.judge.application.sevice.JudgeService;
import com.example.ssoj.judge.domain.model.CaseJudgeResult;
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
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeServiceTest {

    private static final UUID SUBMISSION_11 = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID SUBMISSION_12 = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID SUBMISSION_13 = UUID.fromString("00000000-0000-0000-0000-000000000013");
    private static final UUID SUBMISSION_14 = UUID.fromString("00000000-0000-0000-0000-000000000014");
    private static final UUID SUBMISSION_15 = UUID.fromString("00000000-0000-0000-0000-000000000015");
    private static final UUID SUBMISSION_16 = UUID.fromString("00000000-0000-0000-0000-000000000016");
    private static final UUID SUBMISSION_17 = UUID.fromString("00000000-0000-0000-0000-000000000017");
    private static final UUID SUBMISSION_18 = UUID.fromString("00000000-0000-0000-0000-000000000018");
    private static final UUID CASE_201 = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID CASE_202 = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID CASE_203 = UUID.fromString("00000000-0000-0000-0000-000000000203");
    private static final UUID CASE_204 = UUID.fromString("00000000-0000-0000-0000-000000000204");
    private static final UUID CASE_205 = UUID.fromString("00000000-0000-0000-0000-000000000205");
    private static final UUID CASE_206 = UUID.fromString("00000000-0000-0000-0000-000000000206");
    private static final UUID CASE_207 = UUID.fromString("00000000-0000-0000-0000-000000000207");
    private static final UUID CASE_301 = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CASE_302 = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID CASE_303 = UUID.fromString("00000000-0000-0000-0000-000000000303");

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
                        hiddenTestCase(CASE_201, "1 2", "3"),
                        hiddenTestCase(CASE_202, "5 8", "13")
                )
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_11)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(true, "4\n", "", 0, 12, 64, false, false));

        judgeService.judge(SUBMISSION_11);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(executionGateway, times(1)).execute(any(JudgeContext.class));
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_11), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.WA);
        assertThat(runResult.caseResults()).hasSize(1);
        assertThat(runResult.caseResults().get(0).testCaseId()).isEqualTo(CASE_201);
        assertThat(runResult.caseResults().get(0).result()).isEqualTo(SubmissionResult.WA);
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_mapsJavaCompileErrorToCe_andFinishes() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_12,
                "java",
                List.of(hiddenTestCase(CASE_203, "", "OK"))
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_12)).thenReturn(startedJudging);
        when(executionGateway.supports("java")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(false, "", "Main.java:1: error: ';' expected", 1, 5, 64, false, false));

        judgeService.judge(SUBMISSION_12);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_12), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.CE);
        assertThat(runResult.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.testCaseId()).isEqualTo(CASE_203);
            assertThat(caseResult.result()).isEqualTo(SubmissionResult.CE);
        });
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
        assertThat(runResult.caseResults()).isEmpty();
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
                List.of(hiddenTestCase(CASE_205, "", "OK"))
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
        assertThat(runResult.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.testCaseId()).isEqualTo(CASE_205);
            assertThat(caseResult.result()).isEqualTo(SubmissionResult.TLE);
            assertThat(caseResult.executionTimeMs()).isEqualTo(1000);
        });
        assertThat(finishedAtCaptor.getValue()).isNotNull();
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
        assertThat(runResult.caseResults()).isEmpty();
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
                .thenReturn(new JudgeExecutionResult(true, "OK\n", "", 0, 2400, 64, false, false));

        judgeService.judge(SUBMISSION_17);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_17), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.AC);
        assertThat(runResult.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.testCaseId()).isEqualTo(CASE_207);
            assertThat(caseResult.result()).isEqualTo(SubmissionResult.AC);
            assertThat(caseResult.executionTimeMs()).isEqualTo(2400);
        });
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_doesNotExecuteHiddenCasesAfterFirstFailure() {
        StartedJudging startedJudging = startedJudging(
                SUBMISSION_18,
                "python",
                List.of(
                        hiddenTestCase(CASE_301, "1", "1"),
                        hiddenTestCase(CASE_302, "2", "2"),
                        hiddenTestCase(CASE_303, "3", "3")
                )
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(SUBMISSION_18)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(true, "1\n", "", 0, 3, 32, false, false))
                .thenReturn(new JudgeExecutionResult(true, "wrong\n", "", 0, 4, 32, false, false));

        judgeService.judge(SUBMISSION_18);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        verify(executionGateway, times(2)).execute(any(JudgeContext.class));
        verify(judgePersistenceService).saveResultsAndFinish(eq(SUBMISSION_18), runResultCaptor.capture(), any(Instant.class));

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalResult()).isEqualTo(SubmissionResult.WA);
        assertThat(runResult.caseResults()).hasSize(2);
        assertThat(runResult.caseResults().get(0).testCaseId()).isEqualTo(CASE_301);
        assertThat(runResult.caseResults().get(0).result()).isEqualTo(SubmissionResult.AC);
        assertThat(runResult.caseResults().get(1).testCaseId()).isEqualTo(CASE_302);
        assertThat(runResult.caseResults().get(1).result()).isEqualTo(SubmissionResult.WA);
        assertThat(runResult.caseResults())
                .extracting(CaseJudgeResult::testCaseId)
                .doesNotContain(CASE_303);
    }

    private static StartedJudging startedJudging(
            UUID submissionId,
            String language,
            List<HiddenTestCaseSnapshot> hiddenTestCases
    ) {
        return new StartedJudging(
                submissionId,
                "problem-" + submissionId,
                language,
                "print('hello')",
                1000,
                128,
                hiddenTestCases
        );
    }

    private static HiddenTestCaseSnapshot hiddenTestCase(UUID testCaseId, String input, String expectedOutput) {
        return new HiddenTestCaseSnapshot(testCaseId, input, expectedOutput);
    }
}
