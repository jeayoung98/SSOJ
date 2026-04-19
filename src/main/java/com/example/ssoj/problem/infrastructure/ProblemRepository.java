package com.example.ssoj.problem.infrastructure;

import com.example.ssoj.problem.domain.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
}
