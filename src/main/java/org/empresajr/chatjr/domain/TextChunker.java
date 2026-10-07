package org.empresajr.chatjr.domain;

import java.util.ArrayList;
import java.util.List;

/** Divide o texto do PDF em trechos de tamanho parecido, com um pouco de sobreposição, para a busca por relevância. */
public final class TextChunker {

    /** Tamanho e sobreposição usados em todo o sistema (upload, processamento e chat). */
    public static final int DEFAULT_TARGET = 900;
    public static final int DEFAULT_OVERLAP = 120;

    private TextChunker() {
    }

    public static List<String> chunk(String text) {
        return chunk(text, DEFAULT_TARGET, DEFAULT_OVERLAP);
    }

    public static List<String> chunk(String text, int target, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        List<String> units = new ArrayList<>();
        for (String paragraph : text.replace("\r", "").split("\\n\\s*\\n")) {
            String p = paragraph.replaceAll("\\s+", " ").trim();
            if (p.isEmpty()) {
                continue;
            }
            splitLong(p, target, units);
        }
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (current.length() > 0 && current.length() + 1 + unit.length() > target) {
                String done = current.toString();
                chunks.add(done);
                current = new StringBuilder(tail(done, overlap));
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(unit);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private static void splitLong(String paragraph, int target, List<String> out) {
        int start = 0;
        int length = paragraph.length();
        while (length - start > target) {
            int end = start + target;
            int cut = Math.max(paragraph.lastIndexOf(". ", end), paragraph.lastIndexOf(' ', end));
            if (cut <= start + target / 2) {
                cut = end;
            } else if (paragraph.charAt(cut) == '.') {
                cut++;
            }
            out.add(paragraph.substring(start, cut).trim());
            start = cut;
        }
        String rest = paragraph.substring(start).trim();
        if (!rest.isEmpty()) {
            out.add(rest);
        }
    }

    private static String tail(String text, int overlap) {
        if (overlap <= 0 || text.length() <= overlap) {
            return overlap <= 0 ? "" : text;
        }
        String tail = text.substring(text.length() - overlap);
        int space = tail.indexOf(' ');
        return space < 0 ? tail : tail.substring(space + 1);
    }
}
