package com.example.ssoj.testcase.infrastructure;

import com.example.ssoj.testcase.domain.ProblemTestcase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProblemTestcaseRepository extends JpaRepository<ProblemTestcase, UUID> {

    List<ProblemTestcase> findAllByProblem_Id(String problemId);

    List<ProblemTestcase> findAllByProblem_IdAndHiddenTrueOrderByTestcaseOrderAsc(String problemId);
}
