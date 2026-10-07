package org.empresajr.chatjr.domain;

/** Converte o HTML (já sanitizado) de uma aba em texto simples para busca e para o contexto da IA. */
public final class HtmlText {

    private HtmlText() {
    }

    public static String toPlain(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html
                .replaceAll("(?i)</(p|li|h4|tr|ul|ol|table|thead|tbody)>", "\n")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</t[dh]>", " | ")
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        return text.replaceAll("[ \\t]+", " ").replaceAll(" ?\\n ?", "\n").replaceAll("\\n{3,}", "\n\n").strip();
    }
}
