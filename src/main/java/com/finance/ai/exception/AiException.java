package com.finance.ai.exception;

import org.springframework.http.HttpStatus;

/** Somente códigos e mensagens constantes; nunca guardar causas do provedor. */
public class AiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Long retryAfterSeconds;

    public AiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public AiException(HttpStatus status, String code, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Long retryAfterSeconds() { return retryAfterSeconds; }

    public static AiException invalidResponse() {
        return new AiException(HttpStatus.BAD_GATEWAY, "INVALID_AI_RESPONSE",
                "A IA retornou dados inválidos. Tente novamente ou preencha manualmente.");
    }
}
