package com.example.ssoj.testcase.infrastructure;

import com.example.ssoj.testcase.domain.ProblemTestcase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemTestcaseRepository extends JpaRepository<ProblemTestcase, Long> {

    List<ProblemTestcase> findAllByProblem_Id(Long problemId);

    List<ProblemTestcase> findAllByProblem_IdAndHiddenTrueOrderByTestcaseOrderAsc(Long problemId);
}
