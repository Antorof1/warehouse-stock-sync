package com.github.antorof1.simulator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class UnknownScenarioException extends ErrorResponseException {
    public UnknownScenarioException(String id) {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "No scenario with id %s".formatted(id)
        ), null);
    }
}
