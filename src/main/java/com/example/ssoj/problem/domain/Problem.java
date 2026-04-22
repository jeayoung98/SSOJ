package com.example.ssoj.problem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "problems")
public class Problem {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String difficulty;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "input_description", columnDefinition = "text")
    private String inputDescription;

    @Column(name = "output_description", columnDefinition = "text")
    private String outputDescription;

    @Column(name = "time_limit_ms", nullable = false)
    private Integer timeLimitMs;

    @Column(name = "memory_limit_mb", nullable = false)
    private Integer memoryLimitMb;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Problem() {
    }

}
