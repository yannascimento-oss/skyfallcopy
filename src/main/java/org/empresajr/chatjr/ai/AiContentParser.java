package org.empresajr.chatjr.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.empresajr.chatjr.domain.GeneratedContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Lê o JSON devolvido pela IA. Tolera texto ao redor e cercas de código; descarta o que não vier no formato pedido. */
public final class AiContentParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiContentParser() {
    }

    public static Optional<GeneratedContent> parse(String text) {
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
            String html = node.path("html").asText("");
            if (!node.isObject() || html.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new GeneratedContent(
                    cut(node.path("title").asText(null), 200),
                    html,
                    cut(node.path("shortDescription").asText(null), 500),
                    cut(node.path("whatIsIt").asText(null), 2000),
                    cut(node.path("objective").asText(null), 2000),
                    list(node.path("keyPoints"), 8, 300),
                    list(node.path("suggestedQuestions"), 6, 300),
                    list(node.path("sections"), 20, 120)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static List<String> list(JsonNode array, int maxItems, int maxChars) {
        List<String> items = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode item : array) {
                String value = cut(item.asText("").replaceAll("\\s+", " ").trim(), maxChars);
                if (value != null && !value.isEmpty() && items.size() < maxItems) {
                    items.add(value);
                }
            }
        }
        return items;
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
