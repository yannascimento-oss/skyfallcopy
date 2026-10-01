package org.empresajr.chatjr;

import org.empresajr.chatjr.domain.ExtractiveFormatter;
import org.empresajr.chatjr.domain.PromptBuilder;
import org.empresajr.chatjr.domain.TextChunker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerTest {

    @Test
    void longTextBecomesSeveralBoundedChunksWithoutLosingSentences() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append("Esta e a frase numero ").append(i).append(" do plano de negocios sobre o mercado. ");
        }
        List<String> chunks = TextChunker.chunk(sb.toString(), 900, 120);
        assertTrue(chunks.size() >= 3);
        assertTrue(chunks.stream().allMatch(c -> c.length() <= 1100));
        String all = String.join(" ", chunks);
        assertTrue(all.contains("numero 0 ") && all.contains("numero 39 "));
    }

    @Test
    void emptyInputGivesNoChunksAndShortParagraphsStayTogether() {
        assertTrue(TextChunker.chunk("  ", 900, 120).isEmpty());
        assertTrue(TextChunker.chunk(null, 900, 120).isEmpty());
        assertEquals(1, TextChunker.chunk("Um.\n\nDois.\n\nTres.", 900, 0).size());
    }

    @Test
    void extractiveFormatterBuildsHeadingsAndParagraphsAndEscapesText() {
        ExtractiveFormatter.Result r = ExtractiveFormatter.format(
                "MERCADO ALVO\nO mercado cresce\nmuito rapido.\n\nTamanho do mercado\nSao 10 milhoes <b>.\n", 5000);
        assertTrue(r.html().startsWith("<h4>MERCADO ALVO</h4>"));
        assertTrue(r.html().contains("<p>O mercado cresce muito rapido.</p>"), "linha quebrada não vira título");
        assertTrue(r.html().contains("&lt;b&gt;") && !r.html().contains("<b>"));
        assertEquals(List.of("MERCADO ALVO", "Tamanho do mercado"), r.sections());
        assertTrue(ExtractiveFormatter.format("", 100).html().isEmpty());
    }

    @Test
    void promptIsolatesTheDocumentAndTruncatesHugeOnes() {
        String user = PromptBuilder.processingUser("Mercado", "Norte Fit", "texto do plano");
        assertTrue(user.contains("Etapa: Mercado") && user.contains("<documento>\ntexto do plano\n</documento>"));
        String big = PromptBuilder.processingUser("X", "Y", "a".repeat(PromptBuilder.MAX_DOCUMENT_CHARS + 500));
        assertTrue(big.contains("foi cortado") && big.length() < PromptBuilder.MAX_DOCUMENT_CHARS + 400);
        assertTrue(PromptBuilder.processingSystem().contains("nunca instrução"));
        assertFalse(PromptBuilder.processingSystem().isBlank());
    }
}
