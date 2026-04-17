package com.example.ssoj.worker;

import com.example.ssoj.problem.Problem;
import com.example.ssoj.submission.Submission;
import com.example.ssoj.submission.SubmissionCaseResult;
import com.example.ssoj.submission.SubmissionCaseResultRepository;
import com.example.ssoj.submission.SubmissionRepository;
import com.example.ssoj.submission.SubmissionStatus;
import com.example.ssoj.testcase.TestCase;
import com.example.ssoj.testcase.TestCaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private SubmissionCaseResultRepository submissionCaseResultRepository;

    @Mock
    private LanguageExecutor languageExecutor;

    @Test
    void judge_finishesWithWaAndStoresEachCaseResult() {
        Submission submission = submission(11L, "python", SubmissionStatus.PENDING, problem(101L, 1000, 128));
        TestCase firstCase = testCase(201L, submission.getProblem(), "1 2", "3", true);
        TestCase secondCase = testCase(202L, submission.getProblem(), "5 8", "13", true);

        JudgeService judgeService = new JudgeService(
                submissionRepository,
                testCaseRepository,
                submissionCaseResultRepository,
                List.of(languageExecutor)
        );

        when(submissionRepository.findById(11L)).thenReturn(Optional.of(submission));
        when(languageExecutor.supports("python")).thenReturn(true);
        when(testCaseRepository.findAllByProblem_IdAndHiddenTrueOrderByIdAsc(101L)).thenReturn(List.of(firstCase, secondCase));
        when(languageExecutor.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(true, "4\n", "", 0, 12, 64, false, false))
                .thenReturn(new JudgeExecutionResult(true, "13\n", "", 0, 10, 64, false, false));

        judgeService.judge(11L);

        ArgumentCaptor<SubmissionCaseResult> caseResultCaptor = ArgumentCaptor.forClass(SubmissionCaseResult.class);
        verify(submissionCaseResultRepository, times(2)).save(caseResultCaptor.capture());

        List<SubmissionCaseResult> storedResults = caseResultCaptor.getAllValues();
        assertThat(storedResults).hasSize(2);
        assertThat(storedResults.get(0).getStatus()).isEqualTo(SubmissionStatus.WA);
        assertThat(storedResults.get(1).getStatus()).isEqualTo(SubmissionStatus.AC);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.WA);
        assertThat(submission.getStartedAt()).isNotNull();
        assertThat(submission.getFinishedAt()).isNotNull();
    }

    @Test
    void judge_mapsJavaCompileErrorToCe() {
        Submission submission = submission(12L, "java", SubmissionStatus.PENDING, problem(102L, 1000, 128));
        TestCase testCase = testCase(203L, submission.getProblem(), "", "OK", true);

        JudgeService judgeService = new JudgeService(
                submissionRepository,
                testCaseRepository,
                submissionCaseResultRepository,
                List.of(languageExecutor)
        );

        when(submissionRepository.findById(12L)).thenReturn(Optional.of(submission));
        when(languageExecutor.supports("java")).thenReturn(true);
        when(testCaseRepository.findAllByProblem_IdAndHiddenTrueOrderByIdAsc(102L)).thenReturn(List.of(testCase));
        when(languageExecutor.execute(any(JudgeContext.class)))
                .thenReturn(new JudgeExecutionResult(false, "", "Main.java:1: error: ';' expected", 1, 5, 64, false, false));

        judgeService.judge(12L);

        ArgumentCaptor<SubmissionCaseResult> caseResultCaptor = ArgumentCaptor.forClass(SubmissionCaseResult.class);
        verify(submissionCaseResultRepository).save(caseResultCaptor.capture());

        assertThat(caseResultCaptor.getValue().getStatus()).isEqualTo(SubmissionStatus.CE);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.CE);
    }

    @Test
    void judge_marksSubmissionAsSystemErrorWhenExecutorThrows() {
        Submission submission = submission(13L, "cpp", SubmissionStatus.PENDING, problem(103L, 1000, 128));
        TestCase testCase = testCase(204L, submission.getProblem(), "", "OK", true);

        JudgeService judgeService = new JudgeService(
                submissionRepository,
                testCaseRepository,
                submissionCaseResultRepository,
                List.of(languageExecutor)
        );

        when(submissionRepository.findById(13L)).thenReturn(Optional.of(submission));
        when(languageExecutor.supports("cpp")).thenReturn(true);
        when(testCaseRepository.findAllByProblem_IdAndHiddenTrueOrderByIdAsc(103L)).thenReturn(List.of(testCase));
        when(languageExecutor.execute(any(JudgeContext.class))).thenThrow(new RuntimeException("docker failed"));

        judgeService.judge(13L);

        verify(submissionCaseResultRepository, never()).save(any(SubmissionCaseResult.class));
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SYSTEM_ERROR);
        assertThat(submission.getStartedAt()).isNotNull();
        assertThat(submission.getFinishedAt()).isNotNull();
    }

    @Test
    void judge_skipsCompletedSubmission() {
        Submission submission = submission(14L, "python", SubmissionStatus.AC, problem(104L, 1000, 128));

        JudgeService judgeService = new JudgeService(
                submissionRepository,
                testCaseRepository,
                submissionCaseResultRepository,
                List.of(languageExecutor)
        );

        when(submissionRepository.findById(14L)).thenReturn(Optional.of(submission));

        judgeService.judge(14L);

        verify(testCaseRepository, never()).findAllByProblem_IdAndHiddenTrueOrderByIdAsc(any());
        verify(submissionCaseResultRepository, never()).save(any(SubmissionCaseResult.class));
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.AC);
    }

    @Test
    void judge_marksSubmissionAsTleWhenExecutorTimesOut() {
        Submission submission = submission(15L, "cpp", SubmissionStatus.PENDING, problem(105L, 1000, 128));
        TestCase testCase = testCase(205L, submission.getProblem(), "", "OK", true);

        JudgeService judgeService = new JudgeService(
                submissionRepository,
                testCaseRepository,
                submissionCaseResultRepository,
                List.of(languageExecutor)
        );

        when(submissionRepository.findById(15L)).thenReturn(Optional.of(submission));
        when(languageExecutor.supports("cpp")).thenReturn(true);
        when(testCaseRepository.findAllByProblem_IdAndHiddenTrueOrderByIdAsc(105L)).thenReturn(List.of(testCase));
        when(languageExecutor.execute(any(JudgeContext.class)))
                .thenReturn(JudgeExecutionResult.timeout(1000));

        judgeService.judge(15L);

        ArgumentCaptor<SubmissionCaseResult> caseResultCaptor = ArgumentCaptor.forClass(SubmissionCaseResult.class);
        verify(submissionCaseResultRepository).save(caseResultCaptor.capture());

        assertThat(caseResultCaptor.getValue().getStatus()).isEqualTo(SubmissionStatus.TLE);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.TLE);
        assertThat(submission.getFinishedAt()).isNotNull();
    }

    @Test
    void judge_marksSubmissionAsSystemErrorWhenNoExecutorMatchesLanguage() {
        Submission submission = submission(16L, "unknown", SubmissionStatus.PENDING, problem(106L, 1000, 128));

        JudgeService judgeService = new JudgeService(
                submissionRepository,
                testCaseRepository,
                submissionCaseResultRepository,
                List.of(languageExecutor)
        );

        when(submissionRepository.findById(16L)).thenReturn(Optional.of(submission));
        when(languageExecutor.supports("unknown")).thenReturn(false);

        judgeService.judge(16L);

        verify(testCaseRepository, never()).findAllByProblem_IdAndHiddenTrueOrderByIdAsc(any());
        verify(submissionCaseResultRepository, never()).save(any(SubmissionCaseResult.class));
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SYSTEM_ERROR);
        assertThat(submission.getStartedAt()).isNotNull();
        assertThat(submission.getFinishedAt()).isNotNull();
    }

    private static Problem problem(Long id, int timeLimitMs, int memoryLimitMb) {
        Problem problem = instantiate(Problem.class);
        ReflectionTestUtils.setField(problem, "id", id);
        ReflectionTestUtils.setField(problem, "title", "Two Sum");
        ReflectionTestUtils.setField(problem, "description", "desc");
        ReflectionTestUtils.setField(problem, "timeLimitMs", timeLimitMs);
        ReflectionTestUtils.setField(problem, "memoryLimitMb", memoryLimitMb);
        return problem;
    }

    private static Submission submission(Long id, String language, SubmissionStatus status, Problem problem) {
        Submission submission = instantiate(Submission.class);
        ReflectionTestUtils.setField(submission, "id", id);
        ReflectionTestUtils.setField(submission, "problem", problem);
        ReflectionTestUtils.setField(submission, "language", language);
        ReflectionTestUtils.setField(submission, "sourceCode", "print('hello')");
        ReflectionTestUtils.setField(submission, "status", status);
        ReflectionTestUtils.setField(submission, "createdAt", Instant.now());
        return submission;
    }

    private static TestCase testCase(Long id, Problem problem, String input, String output, boolean hidden) {
        TestCase testCase = instantiate(TestCase.class);
        ReflectionTestUtils.setField(testCase, "id", id);
        ReflectionTestUtils.setField(testCase, "problem", problem);
        ReflectionTestUtils.setField(testCase, "input", input);
        ReflectionTestUtils.setField(testCase, "output", output);
        ReflectionTestUtils.setField(testCase, "hidden", hidden);
        return testCase;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to instantiate " + type.getName(), exception);
        }
    }
}
