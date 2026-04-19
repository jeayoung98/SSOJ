package com.example.ssoj.worker;

import com.example.ssoj.submission.SubmissionStatus;
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

    @Mock
    private JudgePersistenceService judgePersistenceService;

    @Mock
    private ExecutionGateway executionGateway;

    @Test
    void judge_stopsAtFirstFailure_andFinishesWithWa() {
        StartedJudging startedJudging = startedJudging(
                11L,
                "python",
                List.of(
                        hiddenTestCase(201L, "1 2", "3"),
                        hiddenTestCase(202L, "5 8", "13")
                )
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(11L)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(true, "4\n", "", 0, 12, 64, false, false));

        judgeService.judge(11L);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(executionGateway, times(1)).execute(any(JudgeContext.class));
        verify(judgePersistenceService).saveResultsAndFinish(eq(11L), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalStatus()).isEqualTo(SubmissionStatus.WA);
        assertThat(runResult.caseResults()).hasSize(1);
        assertThat(runResult.caseResults().get(0).testCaseId()).isEqualTo(201L);
        assertThat(runResult.caseResults().get(0).status()).isEqualTo(SubmissionStatus.WA);
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_mapsJavaCompileErrorToCe_andFinishes() {
        StartedJudging startedJudging = startedJudging(
                12L,
                "java",
                List.of(hiddenTestCase(203L, "", "OK"))
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(12L)).thenReturn(startedJudging);
        when(executionGateway.supports("java")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(false, "", "Main.java:1: error: ';' expected", 1, 5, 64, false, false));

        judgeService.judge(12L);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(judgePersistenceService).saveResultsAndFinish(eq(12L), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalStatus()).isEqualTo(SubmissionStatus.CE);
        assertThat(runResult.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.testCaseId()).isEqualTo(203L);
            assertThat(caseResult.status()).isEqualTo(SubmissionStatus.CE);
        });
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_marksSubmissionAsSystemErrorWhenExecutorThrows_andStillFinishes() {
        StartedJudging startedJudging = startedJudging(
                13L,
                "cpp",
                List.of(hiddenTestCase(204L, "", "OK"))
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(13L)).thenReturn(startedJudging);
        when(executionGateway.supports("cpp")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class))).thenThrow(new RuntimeException("docker failed"));

        judgeService.judge(13L);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(judgePersistenceService).saveResultsAndFinish(eq(13L), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalStatus()).isEqualTo(SubmissionStatus.SYSTEM_ERROR);
        assertThat(runResult.caseResults()).isEmpty();
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_skipsAlreadyCompletedSubmission_withoutFinishingAgain() {
        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(14L)).thenReturn(null);

        judgeService.judge(14L);

        verify(executionGateway, never()).supports(any());
        verify(executionGateway, never()).execute(any(JudgeContext.class));
        verify(judgePersistenceService, never()).saveResultsAndFinish(any(), any(), any());
    }

    @Test
    void judge_stopsAtTimeout_andFinishesWithTle() {
        StartedJudging startedJudging = startedJudging(
                15L,
                "cpp",
                List.of(hiddenTestCase(205L, "", "OK"))
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(15L)).thenReturn(startedJudging);
        when(executionGateway.supports("cpp")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(JudgeExecutionResult.timeout(1000));

        judgeService.judge(15L);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(judgePersistenceService).saveResultsAndFinish(eq(15L), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalStatus()).isEqualTo(SubmissionStatus.TLE);
        assertThat(runResult.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.testCaseId()).isEqualTo(205L);
            assertThat(caseResult.status()).isEqualTo(SubmissionStatus.TLE);
            assertThat(caseResult.executionTimeMs()).isEqualTo(1000);
        });
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_finishesWithSystemErrorWhenLanguageIsUnsupported() {
        StartedJudging startedJudging = startedJudging(
                16L,
                "unknown",
                List.of(hiddenTestCase(206L, "", "OK"))
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(16L)).thenReturn(startedJudging);
        when(executionGateway.supports("unknown")).thenReturn(false);

        judgeService.judge(16L);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(executionGateway, never()).execute(any(JudgeContext.class));
        verify(judgePersistenceService).saveResultsAndFinish(eq(16L), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalStatus()).isEqualTo(SubmissionStatus.SYSTEM_ERROR);
        assertThat(runResult.caseResults()).isEmpty();
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_keepsAcWhenSingleHiddenCaseSucceeds() {
        StartedJudging startedJudging = startedJudging(
                17L,
                "python",
                List.of(hiddenTestCase(207L, "", "OK"))
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(17L)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(true, "OK\n", "", 0, 2400, 64, false, false));

        judgeService.judge(17L);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        ArgumentCaptor<Instant> finishedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(judgePersistenceService).saveResultsAndFinish(eq(17L), runResultCaptor.capture(), finishedAtCaptor.capture());

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalStatus()).isEqualTo(SubmissionStatus.AC);
        assertThat(runResult.caseResults()).singleElement().satisfies(caseResult -> {
            assertThat(caseResult.testCaseId()).isEqualTo(207L);
            assertThat(caseResult.status()).isEqualTo(SubmissionStatus.AC);
            assertThat(caseResult.executionTimeMs()).isEqualTo(2400);
        });
        assertThat(finishedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void judge_doesNotExecuteHiddenCasesAfterFirstFailure() {
        StartedJudging startedJudging = startedJudging(
                18L,
                "python",
                List.of(
                        hiddenTestCase(301L, "1", "1"),
                        hiddenTestCase(302L, "2", "2"),
                        hiddenTestCase(303L, "3", "3")
                )
        );

        JudgeService judgeService = new JudgeService(
                judgePersistenceService,
                executionGateway
        );

        when(judgePersistenceService.startJudging(18L)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(true, "1\n", "", 0, 3, 32, false, false))
                .thenReturn(new JudgeExecutionResult(true, "wrong\n", "", 0, 4, 32, false, false));

        judgeService.judge(18L);

        ArgumentCaptor<JudgeRunResult> runResultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        verify(executionGateway, times(2)).execute(any(JudgeContext.class));
        verify(judgePersistenceService).saveResultsAndFinish(eq(18L), runResultCaptor.capture(), any(Instant.class));

        JudgeRunResult runResult = runResultCaptor.getValue();
        assertThat(runResult.finalStatus()).isEqualTo(SubmissionStatus.WA);
        assertThat(runResult.caseResults()).hasSize(2);
        assertThat(runResult.caseResults().get(0).testCaseId()).isEqualTo(301L);
        assertThat(runResult.caseResults().get(0).status()).isEqualTo(SubmissionStatus.AC);
        assertThat(runResult.caseResults().get(1).testCaseId()).isEqualTo(302L);
        assertThat(runResult.caseResults().get(1).status()).isEqualTo(SubmissionStatus.WA);
        assertThat(runResult.caseResults())
                .extracting(CaseJudgeResult::testCaseId)
                .doesNotContain(303L);
    }

    private static StartedJudging startedJudging(
            Long submissionId,
            String language,
            List<HiddenTestCaseSnapshot> hiddenTestCases
    ) {
        return new StartedJudging(
                submissionId,
                100L + submissionId,
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
}
