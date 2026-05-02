package com.example.ssoj.judge;

import com.example.ssoj.judge.application.port.ExecutionGateway;
import com.example.ssoj.judge.application.sevice.JudgePersistenceService;
import com.example.ssoj.judge.application.sevice.JudgeService;
import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
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

    @Mock
    private JudgePersistenceService judgePersistenceService;

    @Mock
    private ExecutionGateway executionGateway;

    @Test
    void judge_callsExecuteSubmissionOnceAndPersistsReturnedRunResult() {
        StartedJudging startedJudging = startedJudging(List.of(
                new HiddenTestCaseSnapshot(1L, 1, "1 2\n", "3\n"),
                new HiddenTestCaseSnapshot(2L, 2, "2 3\n", "5\n")
        ));
        JudgeRunResult runnerResult = new JudgeRunResult(SubmissionResult.WA, 8, 256, 2);
        JudgeService judgeService = new JudgeService(judgePersistenceService, executionGateway);

        when(judgePersistenceService.startJudging(10L)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.executeSubmission(any(JudgeRunContext.class))).thenReturn(runnerResult);

        judgeService.judge(10L);

        ArgumentCaptor<JudgeRunContext> contextCaptor = ArgumentCaptor.forClass(JudgeRunContext.class);
        ArgumentCaptor<JudgeRunResult> resultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        verify(executionGateway, times(1)).executeSubmission(contextCaptor.capture());
        verify(judgePersistenceService).saveResultsAndFinish(eq(10L), resultCaptor.capture(), any(Instant.class));

        JudgeRunContext context = contextCaptor.getValue();
        assertThat(context.hiddenTestCases()).hasSize(2);
        assertThat(context.timeLimitMs()).isEqualTo(1000);
        assertThat(context.memoryLimitMb()).isEqualTo(128);

        assertThat(resultCaptor.getValue()).isEqualTo(runnerResult);
    }

    @Test
    void judge_returnsJudgeErrorWithoutRunnerCallWhenThereAreNoTestcases() {
        JudgeService judgeService = new JudgeService(judgePersistenceService, executionGateway);
        when(judgePersistenceService.startJudging(11L)).thenReturn(startedJudging(List.of()));
        when(executionGateway.supports("python")).thenReturn(true);

        judgeService.judge(11L);

        ArgumentCaptor<JudgeRunResult> resultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        verify(executionGateway, never()).executeSubmission(any());
        verify(judgePersistenceService).saveResultsAndFinish(eq(11L), resultCaptor.capture(), any(Instant.class));
        assertThat(resultCaptor.getValue()).isEqualTo(JudgeRunResult.judgeError());
    }

    @Test
    void judge_executesPublicTestcasesWhenHiddenCasesAreMissing() {
        StartedJudging startedJudging = new StartedJudging(
                10L,
                20L,
                "python",
                "print(1)",
                1000,
                128,
                List.of(new HiddenTestCaseSnapshot(1L, 1, "", "1\n")),
                0
        );
        JudgeRunResult runnerResult = new JudgeRunResult(SubmissionResult.AC, 7, 128, null);
        JudgeService judgeService = new JudgeService(judgePersistenceService, executionGateway);

        when(judgePersistenceService.startJudging(13L)).thenReturn(startedJudging);
        when(executionGateway.supports("python")).thenReturn(true);
        when(executionGateway.executeSubmission(any(JudgeRunContext.class))).thenReturn(runnerResult);

        judgeService.judge(13L);

        ArgumentCaptor<JudgeRunContext> contextCaptor = ArgumentCaptor.forClass(JudgeRunContext.class);
        ArgumentCaptor<JudgeRunResult> resultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        verify(executionGateway).executeSubmission(contextCaptor.capture());
        verify(judgePersistenceService).saveResultsAndFinish(eq(13L), resultCaptor.capture(), any(Instant.class));
        assertThat(contextCaptor.getValue().hiddenTestCases()).hasSize(1);
        assertThat(resultCaptor.getValue()).isEqualTo(runnerResult);
    }

    @Test
    void judge_returnsSystemErrorWhenLanguageIsUnsupported() {
        JudgeService judgeService = new JudgeService(judgePersistenceService, executionGateway);
        when(judgePersistenceService.startJudging(12L)).thenReturn(startedJudging(List.of(
                new HiddenTestCaseSnapshot(1L, 1, "", "")
        )));
        when(executionGateway.supports("ruby")).thenReturn(false);

        judgeService.judge(12L);

        ArgumentCaptor<JudgeRunResult> resultCaptor = ArgumentCaptor.forClass(JudgeRunResult.class);
        verify(executionGateway, never()).executeSubmission(any());
        verify(judgePersistenceService).saveResultsAndFinish(eq(12L), resultCaptor.capture(), any(Instant.class));
        assertThat(resultCaptor.getValue().finalResult()).isEqualTo(SubmissionResult.SYSTEM_ERROR);
        assertThat(resultCaptor.getValue().failedTestcaseOrder()).isNull();
    }

    private StartedJudging startedJudging(List<HiddenTestCaseSnapshot> hiddenTestCases) {
        return new StartedJudging(10L, 20L, "python", "print(1)", 1000, 128, hiddenTestCases);
    }
}
