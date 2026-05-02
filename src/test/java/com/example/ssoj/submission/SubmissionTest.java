package com.example.ssoj.submission;

import com.example.ssoj.submission.domain.Submission;
import com.example.ssoj.submission.domain.SubmissionResult;
import com.example.ssoj.submission.domain.SubmissionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionTest {

    @Test
    void updateJudgedResult_doesNotClearExistingMetricsWhenLaterUpdateHasNullMetrics() {
        Submission submission = instantiate(Submission.class);
        ReflectionTestUtils.setField(submission, "status", SubmissionStatus.PENDING);

        submission.updateJudgedResult(SubmissionResult.AC, 12, 256, null, Instant.parse("2026-05-03T00:00:00Z"));
        submission.updateJudgedResult(SubmissionResult.AC, null, null, null, Instant.parse("2026-05-03T00:00:01Z"));

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(submission.getResult()).isEqualTo(SubmissionResult.AC);
        assertThat(submission.getExecutionTimeMs()).isEqualTo(12);
        assertThat(submission.getMemoryKb()).isEqualTo(256);
        assertThat(submission.getFailedTestcaseOrder()).isNull();
    }

    @Test
    void updateJudgedResult_keepsFailedTestcaseOrderNullForAccepted() {
        Submission submission = instantiate(Submission.class);
        ReflectionTestUtils.setField(submission, "status", SubmissionStatus.PENDING);

        submission.updateJudgedResult(SubmissionResult.AC, 12, 256, 2, Instant.parse("2026-05-03T00:00:00Z"));

        assertThat(submission.getFailedTestcaseOrder()).isNull();
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
