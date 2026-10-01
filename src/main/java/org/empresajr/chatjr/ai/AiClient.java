package org.empresajr.chatjr.ai;

public interface AiClient {

    AiResult complete(AiRequest request) throws AiException;
}
