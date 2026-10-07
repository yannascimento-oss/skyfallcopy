package org.empresajr.chatjr.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/** Lê o JSON de resposta do chat. Tolera texto ao redor e cercas de código. */
public final class ChatAnswerParser {

    public record Parsed(boolean answerable, String html, String source, boolean inference, String queryType,
                         String theme) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ChatAnswerParser() {
    }

    public static Optional<Parsed> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        try {
            JsonNode node = MAPPER.readTree(text.substring(start, end + 1));
            if (!node.isObject()) {
                return Optional.empty();
            }
            String html = node.path("html").asText("").trim();
            boolean answerable = node.has("answerable") ? node.path("answerable").asBoolean(true) : !html.isEmpty();
            if (answerable && html.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Parsed(answerable, html, blankToNull(node.path("source").asText("")),
                    node.path("inference").asBoolean(false), blankToNull(node.path("queryType").asText("")),
                    blankToNull(node.path("theme").asText(""))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String blankToNull(String text) {
        String t = text == null ? "" : text.trim();
        return t.isEmpty() ? null : t;
    }
}
