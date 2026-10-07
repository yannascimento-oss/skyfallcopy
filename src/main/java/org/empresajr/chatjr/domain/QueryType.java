package org.empresajr.chatjr.domain;

/** Tipo da pergunta do cliente, usado nos indicadores. */
public enum QueryType {
    INFORMACAO,
    DUVIDA,
    INTERPRETACAO,
    DECISAO;

    public static QueryType parse(String value, QueryType fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
