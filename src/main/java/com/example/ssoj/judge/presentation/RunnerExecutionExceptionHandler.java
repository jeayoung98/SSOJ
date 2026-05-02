package com.example.ssoj.judge.presentation;

import com.example.ssoj.judge.application.sevice.RunnerBusyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RunnerExecutionExceptionHandler {

    @ExceptionHandler(RunnerBusyException.class)
    ResponseEntity<String> handleRunnerBusy(RunnerBusyException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(exception.getMessage());
    }
}
