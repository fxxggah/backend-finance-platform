package com.finance.ai.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.finance.ai")
public class AiExceptionHandler {
    @ExceptionHandler(AiException.class)
    public ProblemDetail handle(AiException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Não foi possível preparar o rascunho");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
