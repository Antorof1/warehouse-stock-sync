package com.github.antorof1.simulator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ScenarioBusyException extends ErrorResponseException {
    public ScenarioBusyException(String runningScenario) {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Scenario %s is still running".formatted(runningScenario)
        ), null);
    }
}
