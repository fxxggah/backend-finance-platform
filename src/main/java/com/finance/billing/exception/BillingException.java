package com.finance.billing.exception;

import org.springframework.http.HttpStatus;

public class BillingException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public BillingException(HttpStatus status, String code, String message) { super(message); this.status = status; this.code = code; }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public static BillingException unavailable() { return new BillingException(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_UNAVAILABLE", "A cobrança está temporariamente indisponível. Tente novamente mais tarde."); }
    public static BillingException invalid() { return new BillingException(HttpStatus.BAD_GATEWAY, "INVALID_STRIPE_RESPONSE", "Não foi possível validar os dados da cobrança."); }
    public static BillingException busy() { return new BillingException(HttpStatus.CONFLICT, "BILLING_OPERATION_IN_PROGRESS", "Há uma operação de cobrança em andamento. Aguarde e tente novamente."); }
    public static BillingException review() { return new BillingException(HttpStatus.UNPROCESSABLE_ENTITY, "BILLING_REVIEW_REQUIRED", "Esta cobrança precisa de revisão. Entre em contato com o suporte."); }
}
