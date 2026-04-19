package com.example.ssoj.testcase.infrastructure;

import com.example.ssoj.testcase.domain.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findAllByProblem_Id(Long problemId);

    List<TestCase> findAllByProblem_IdAndHiddenTrueOrderByIdAsc(Long problemId);
}
