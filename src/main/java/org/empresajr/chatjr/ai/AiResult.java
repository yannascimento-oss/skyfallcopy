package org.empresajr.chatjr.ai;

public record AiResult(String text, int inputTokens, int outputTokens, boolean truncated) {
}
