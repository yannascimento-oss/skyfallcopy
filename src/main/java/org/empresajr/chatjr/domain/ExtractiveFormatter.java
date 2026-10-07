package org.empresajr.chatjr.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Plano B sem IA: transforma o texto extraído do PDF em HTML simples (títulos e parágrafos), sem reescrever nada.
 * Usado quando não há chave da IA ou quando a IA falha.
 */
public final class ExtractiveFormatter {

    public record Result(String html, List<String> sections) {
    }

    private ExtractiveFormatter() {
    }

    public static Result format(String text, int maxChars) {
        StringBuilder html = new StringBuilder();
        List<String> sections = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return new Result("", sections);
        }
        String[] lines = text.replace("\r", "").split("\n", -1);
        List<String> paragraph = new ArrayList<>();
        boolean blockStart = true;   // a linha anterior foi vazia, um título, ou não existe
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                flush(paragraph, html);
                blockStart = true;
                continue;
            }
            String next = i + 1 < lines.length ? lines[i + 1].strip() : "";
            if (blockStart && looksLikeHeading(line) && !continuesInLowercase(next)) {
                flush(paragraph, html);
                html.append("<h4>").append(escape(line)).append("</h4>");
                if (sections.size() < 20) {
                    sections.add(line.length() > 120 ? line.substring(0, 120) : line);
                }
                blockStart = true;
            } else {
                paragraph.add(line);
                blockStart = false;
            }
            if (html.length() >= maxChars) {
                break;
            }
        }
        flush(paragraph, html);
        return new Result(html.toString(), sections);
    }

    private static void flush(List<String> paragraph, StringBuilder html) {
        if (!paragraph.isEmpty()) {
            html.append("<p>").append(escape(String.join(" ", paragraph))).append("</p>");
            paragraph.clear();
        }
    }

    private static boolean looksLikeHeading(String line) {
        if (line.length() < 3 || line.length() > 70) {
            return false;
        }
        char last = line.charAt(line.length() - 1);
        if (last == '.' || last == ',' || last == ';') {
            return false;
        }
        int words = line.split("\\s+").length;
        int first = line.codePointAt(0);
        return words <= 9 && (Character.isUpperCase(first) || Character.isDigit(first));
    }

    /** A linha seguinte começa em minúscula: então a linha atual é só o começo de um parágrafo quebrado. */
    private static boolean continuesInLowercase(String next) {
        return !next.isEmpty() && Character.isLowerCase(next.codePointAt(0));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
