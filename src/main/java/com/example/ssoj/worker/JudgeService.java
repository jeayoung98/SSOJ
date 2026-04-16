package com.example.ssoj.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    public void judge(Long submissionId) {
        log.info("JudgeService received submissionId={}", submissionId);
    }
}
