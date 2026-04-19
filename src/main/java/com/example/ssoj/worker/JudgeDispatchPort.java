package com.example.ssoj.worker;

public interface JudgeDispatchPort {

    void dispatch(JudgeDispatchCommand command);
}
