package org.empresajr.chatjr;

import org.empresajr.chatjr.ai.ChatAnswerParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatAnswerParserTest {

    @Test
    void readsAFullAnswer() {
        var p = ChatAnswerParser.parse("{\"answerable\":true,\"html\":\"<p>x</p>\",\"source\":\"Mercado\",\"inference\":true,"
                + "\"queryType\":\"DECISAO\",\"theme\":\"Preço\"}").orElseThrow();
        assertTrue(p.answerable() && p.inference());
        assertEquals("Mercado", p.source());
        assertEquals("DECISAO", p.queryType());
    }

    @Test
    void toleratesFencesAndTextAround() {
        assertEquals("<p>ok</p>", ChatAnswerParser.parse("Claro:\n```json\n{\"html\":\"<p>ok</p>\"}\n```").orElseThrow().html());
    }

    @Test
    void notAnswerableNeedsNoHtmlButAnswerableDoes() {
        assertFalse(ChatAnswerParser.parse("{\"answerable\":false}").orElseThrow().answerable());
        assertTrue(ChatAnswerParser.parse("{\"answerable\":true,\"html\":\"\"}").isEmpty());
    }

    @Test
    void rejectsGarbage() {
        assertTrue(ChatAnswerParser.parse("sem json").isEmpty());
        assertTrue(ChatAnswerParser.parse("{ quebrado").isEmpty());
        assertTrue(ChatAnswerParser.parse("[1,2]").isEmpty());
        assertTrue(ChatAnswerParser.parse(null).isEmpty());
    }

    @Test
    void blankFieldsBecomeNull() {
        var p = ChatAnswerParser.parse("{\"html\":\"<p>x</p>\",\"source\":\"  \",\"theme\":\"\"}").orElseThrow();
        assertEquals(null, p.source());
        assertEquals(null, p.theme());
    }
}
