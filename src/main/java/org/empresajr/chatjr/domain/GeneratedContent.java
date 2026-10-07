package org.empresajr.chatjr.domain;

import java.util.List;

/** Conteúdo de uma aba produzido a partir do PDF (pela IA ou pelo formatador sem IA). */
public record GeneratedContent(String title, String html, String shortDescription, String whatIsIt,
                               String objective, List<String> keyPoints, List<String> suggestedQuestions,
                               List<String> sections) {
}
