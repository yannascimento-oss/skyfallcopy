package org.empresajr.chatjr.ai;

/** Falha na chamada à IA. A mensagem é segura para mostrar ao usuário (nunca traz a chave). */
public class AiException extends Exception {

    private final int httpStatus;

    public AiException(int httpStatus, String userMessage) {
        super(userMessage);
        this.httpStatus = httpStatus;
    }

    /** 0 quando a falha foi de rede ou tempo esgotado. */
    public int getHttpStatus() {
        return httpStatus;
    }
}
