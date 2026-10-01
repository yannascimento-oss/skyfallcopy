package org.empresajr.chatjr.ai;

/** Pedido à IA. O toString nunca mostra a chave, para ela não vazar em log. */
public record AiRequest(String apiKey, String model, String system, String user, int maxTokens, double temperature) {

    @Override
    public String toString() {
        return "AiRequest[model=" + model + ", maxTokens=" + maxTokens + "]";
    }
}
