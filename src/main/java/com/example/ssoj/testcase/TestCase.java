package com.example.ssoj.testcase;

import com.example.ssoj.problem.Problem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_case")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false, columnDefinition = "text")
    private String input;

    @Column(nullable = false, columnDefinition = "text")
    private String output;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    protected TestCase() {
    }

    public Long getId() {
        return id;
    }

    public Problem getProblem() {
        return problem;
    }

    public String getInput() {
        return input;
    }

    public String getOutput() {
        return output;
    }

    public boolean isHidden() {
        return hidden;
    }
}
