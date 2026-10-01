package org.empresajr.chatjr.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitizador de HTML por lista de permissão. Reescreve o texto do zero: só saem tags permitidas, sem
 * nenhum atributo, e todo o resto vira texto escapado. Serve ao conteúdo escrito pela IA e pela consultoria.
 */
public final class HtmlSanitizer {

    private static final Set<String> ALLOWED = Set.of(
            "p", "b", "strong", "ul", "ol", "li", "h4", "table", "thead", "tbody", "tr", "th", "td", "br");
    /** Tags cujo conteúdo interno também é descartado. */
    private static final Set<String> DROP_WITH_CONTENT = Set.of(
            "script", "style", "iframe", "object", "embed", "noscript", "template", "svg", "math");

    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#\\d{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});");

    private HtmlSanitizer() {
    }

    public static String sanitize(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        int length = html.length();
        StringBuilder out = new StringBuilder(length);
        int i = 0;
        while (i < length) {
            char c = html.charAt(i);
            if (c == '<') {
                if (html.startsWith("<!--", i)) {
                    int end = html.indexOf("-->", i + 4);
                    i = end < 0 ? length : end + 3;
                    continue;
                }
                Matcher tag = TAG.matcher(html).region(i, length);
                if (tag.lookingAt()) {
                    boolean closing = !tag.group(1).isEmpty();
                    String name = tag.group(2).toLowerCase(Locale.ROOT);
                    i = tag.end();
                    if (DROP_WITH_CONTENT.contains(name)) {
                        if (!closing) {
                            int close = indexOfIgnoreCase(html, "</" + name, i);
                            if (close < 0) {
                                i = length;
                            } else {
                                int gt = html.indexOf('>', close);
                                i = gt < 0 ? length : gt + 1;
                            }
                        }
                    } else if (ALLOWED.contains(name)) {
                        if (name.equals("br")) {
                            if (!closing) {
                                out.append("<br>");
                            }
                        } else {
                            out.append('<').append(closing ? "/" : "").append(name).append('>');
                        }
                    }
                    // Tag desconhecida: descarta a tag e mantém o texto de dentro.
                    continue;
                }
                out.append("&lt;");
                i++;
            } else if (c == '&') {
                Matcher entity = ENTITY.matcher(html).region(i, length);
                if (entity.lookingAt()) {
                    out.append(entity.group());
                    i = entity.end();
                } else {
                    out.append("&amp;");
                    i++;
                }
            } else if (c == '>') {
                out.append("&gt;");
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int indexOfIgnoreCase(String text, String needle, int from) {
        int last = text.length() - needle.length();
        for (int k = from; k <= last; k++) {
            if (text.regionMatches(true, k, needle, 0, needle.length())) {
                return k;
            }
        }
        return -1;
    }
}
