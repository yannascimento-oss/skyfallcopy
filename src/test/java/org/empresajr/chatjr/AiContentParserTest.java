package org.empresajr.chatjr;

import org.empresajr.chatjr.ai.AiContentParser;
import org.empresajr.chatjr.domain.GeneratedContent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiContentParserTest {

    @Test
    void readsPlainJson() {
        GeneratedContent c = AiContentParser.parse("{\"title\":\"T\",\"html\":\"<p>x</p>\",\"keyPoints\":[\"a\",\"b\"],\"sections\":[\"S1\"]}").orElseThrow();
        assertEquals("T", c.title());
        assertEquals("<p>x</p>", c.html());
        assertEquals(2, c.keyPoints().size());
        assertEquals(1, c.sections().size());
    }

    @Test
    void toleratesCodeFencesAndTextAroundTheJson() {
        String text = "Claro! Aqui está:\n```json\n{\"html\":\"<p>ok</p>\"}\n```\nEspero ter ajudado.";
        assertEquals("<p>ok</p>", AiContentParser.parse(text).orElseThrow().html());
    }

    @Test
    void rejectsWhatIsNotTheRequestedShape() {
        assertTrue(AiContentParser.parse("sem json nenhum").isEmpty());
        assertTrue(AiContentParser.parse("{\"title\":\"sem html\"}").isEmpty());
        assertTrue(AiContentParser.parse("{\"html\":\"  \"}").isEmpty());
        assertTrue(AiContentParser.parse("{ quebrado").isEmpty());
        assertTrue(AiContentParser.parse(null).isEmpty());
        assertEquals(Optional.empty(), AiContentParser.parse("[1,2,3]"));
    }

    @Test
    void limitsListsAndCleansItems() {
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            points.append("\"ponto ").append(i).append("\",");
        }
        String text = "{\"html\":\"<p>x</p>\",\"keyPoints\":[" + points + "\"  \",\"" + "z".repeat(500) + "\"]}";
        GeneratedContent c = AiContentParser.parse(text).orElseThrow();
        assertEquals(8, c.keyPoints().size());
        assertTrue(c.keyPoints().stream().allMatch(p -> p.length() <= 300 && !p.isBlank()));
    }
}
