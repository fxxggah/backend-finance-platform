package com.finance.billing.exception;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class BillingExceptionHandler {
    @ExceptionHandler(BillingException.class)
    public ResponseEntity<ProblemDetail> handle(BillingException ex) {
        var problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setTitle("Não foi possível concluir a cobrança"); problem.setProperty("code", ex.code());
        var response = ResponseEntity.status(ex.status()).cacheControl(CacheControl.noStore());
        if (ex.status() == HttpStatus.CONFLICT || ex.status() == HttpStatus.SERVICE_UNAVAILABLE) response.header("Retry-After", "30");
        return response.body(problem);
    }
}
