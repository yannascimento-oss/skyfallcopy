package org.empresajr.chatjr.domain;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugs {

    private Slugs() {
    }

    /** "Produto/Serviço" vira "produto-servico". */
    public static String slugify(String text) {
        String base = text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "etapa" : slug;
    }
}
