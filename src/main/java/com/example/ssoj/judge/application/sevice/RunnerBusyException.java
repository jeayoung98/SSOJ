package com.example.ssoj.judge.application.sevice;

public class RunnerBusyException extends RuntimeException {

    public RunnerBusyException(String message) {
        super(message);
    }

    public RunnerBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}
