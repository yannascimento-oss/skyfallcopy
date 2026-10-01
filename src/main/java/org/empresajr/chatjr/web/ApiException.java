package org.empresajr.chatjr.web;

import org.springframework.http.HttpStatus;

/** Erro de negócio com status HTTP e mensagem pronta para o usuário (em português). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
