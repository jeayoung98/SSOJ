package com.example.ssoj.judge.application.port;

import com.example.ssoj.judge.domain.model.JudgeDispatchCommand;

public interface JudgeDispatchPort {

    void dispatch(JudgeDispatchCommand command);
}
