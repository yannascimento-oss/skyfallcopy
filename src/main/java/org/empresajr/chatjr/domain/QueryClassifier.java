package org.empresajr.chatjr.domain;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Classificação simples por palavras-chave, usada quando a IA não informa o tipo da pergunta. */
public final class QueryClassifier {

    private static final List<String> DECISION = List.of("devo ", "deveria", "vale a pena", "recomend", "qual a melhor",
            "qual o melhor", "posso ", "preciso decidir", "compensa", "o que fazer");
    private static final List<String> DOUBT = List.of("nao entendi", "o que significa", "o que e ", "o que sao ",
            "explique", "explica", "como assim", "nao compreendi", "pode detalhar");
    private static final List<String> INTERPRETATION = List.of("por que", "porque", "o que isso", "analise",
            "interprete", "quer dizer", "o que indica", "isso e bom", "isso e ruim");

    private QueryClassifier() {
    }

    public static QueryType classify(String question) {
        String q = " " + normalize(question) + " ";
        if (containsAny(q, DECISION)) {
            return QueryType.DECISAO;
        }
        if (containsAny(q, DOUBT)) {
            return QueryType.DUVIDA;
        }
        if (containsAny(q, INTERPRETATION)) {
            return QueryType.INTERPRETACAO;
        }
        return QueryType.INFORMACAO;
    }

    private static boolean containsAny(String text, List<String> needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        String n = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[?!.,;:]", " ").replaceAll("\\s+", " ").trim();
    }
}
