package com.example.ssoj.worker;

import com.example.ssoj.submission.Submission;
import com.example.ssoj.submission.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);
    private final SubmissionRepository submissionRepository;
    private final List<LanguageExecutor> languageExecutors;

    public JudgeService(SubmissionRepository submissionRepository, List<LanguageExecutor> languageExecutors) {
        this.submissionRepository = submissionRepository;
        this.languageExecutors = languageExecutors;
    }

    @Transactional
    public void judge(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            log.warn("Submission {} does not exist", submissionId);
            return;
        }

        if (submission.isCompleted()) {
            log.info("Submission {} is already completed with status={}", submissionId, submission.getStatus());
            return;
        }

        boolean updated = submission.markAsJudging(Instant.now());
        if (!updated) {
            log.info("Submission {} is ignored because current status={}", submissionId, submission.getStatus());
            return;
        }

        log.info("Submission {} changed from PENDING to JUDGING", submissionId);

        LanguageExecutor executor = findExecutor(submission.getLanguage());
        if (executor == null) {
            log.warn("No LanguageExecutor found for language={}", submission.getLanguage());
            return;
        }

        JudgeContext context = new JudgeContext(
                submission.getId(),
                submission.getProblem().getId(),
                submission.getLanguage(),
                submission.getSourceCode(),
                "",
                submission.getProblem().getTimeLimitMs(),
                submission.getProblem().getMemoryLimitMb()
        );

        log.info("Prepared judge context for submission {} with executor {}", submissionId, executor.getClass().getSimpleName());
    }

    private LanguageExecutor findExecutor(String language) {
        for (LanguageExecutor languageExecutor : languageExecutors) {
            if (languageExecutor.supports(language)) {
                return languageExecutor;
            }
        }

        return null;
    }
}
