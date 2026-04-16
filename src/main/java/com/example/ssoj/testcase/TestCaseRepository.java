package com.example.ssoj.testcase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findAllByProblem_Id(Long problemId);
}
