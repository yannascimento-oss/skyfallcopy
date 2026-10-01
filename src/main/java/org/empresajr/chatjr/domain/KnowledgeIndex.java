package org.empresajr.chatjr.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Busca por relevância (BM25) sobre os trechos das abas que o cliente pode ler.
 * Roda em memória a cada pergunta: o volume de um plano de negócios é pequeno, e assim a busca é idêntica
 * em PostgreSQL e H2 e sempre reflete o que está publicado agora.
 */
public final class KnowledgeIndex {

    public record Doc(Long tabId, String tabName, int index, String text) {
    }

    public record Hit(Doc doc, double score) {
    }

    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final Set<String> STOP = Set.copyOf(List.of("a", "o", "as", "os", "um", "uma", "uns", "umas", "de", "do", "da",
            "dos", "das", "em", "no", "na", "nos", "nas", "por", "para", "com", "sem", "que", "e", "se", "ao", "aos",
            "mais", "como", "mas", "ou", "foi", "sao", "ser", "tem", "ter", "seu", "sua", "seus", "suas", "meu", "minha",
            "qual", "quais", "quanto", "quantos", "quanta", "quando", "onde", "esse", "essa", "isso", "este", "esta",
            "isto", "eu", "voce", "nosso", "nossa", "ha", "ja", "me", "te", "lhe", "ele", "ela", "eles", "elas",
            "pelo", "pela", "entre", "sobre", "ate", "so", "ainda", "tambem", "muito", "pouco", "sera", "vai"));

    private final List<Doc> docs;
    private final List<Map<String, Integer>> termFrequencies = new ArrayList<>();
    private final List<Integer> lengths = new ArrayList<>();
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private final double averageLength;

    public KnowledgeIndex(List<Doc> docs) {
        this.docs = List.copyOf(docs);
        long total = 0;
        for (Doc doc : this.docs) {
            // O nome da etapa conta na busca: "mercado" deve achar a aba Mercado.
            List<String> tokens = tokenize(doc.tabName() + " " + doc.text());
            Map<String, Integer> tf = new HashMap<>();
            for (String token : tokens) {
                tf.merge(token, 1, Integer::sum);
            }
            termFrequencies.add(tf);
            lengths.add(tokens.size());
            total += tokens.size();
            for (String term : tf.keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        this.averageLength = this.docs.isEmpty() ? 1.0 : Math.max(1.0, (double) total / this.docs.size());
    }

    public boolean isEmpty() {
        return docs.isEmpty();
    }

    /** Melhores trechos para a pergunta. Trechos sem nenhuma palavra em comum com a pergunta não aparecem. */
    public List<Hit> search(String query, int limit) {
        Set<String> terms = new HashSet<>(tokenize(query));
        List<Hit> hits = new ArrayList<>();
        int n = docs.size();
        for (int i = 0; i < n; i++) {
            double score = 0;
            Map<String, Integer> tf = termFrequencies.get(i);
            for (String term : terms) {
                Integer f = tf.get(term);
                if (f == null) {
                    continue;
                }
                int df = documentFrequency.getOrDefault(term, 0);
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                double norm = f + K1 * (1 - B + B * lengths.get(i) / averageLength);
                score += idf * (f * (K1 + 1)) / norm;
            }
            if (score > 0) {
                hits.add(new Hit(docs.get(i), score));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.size() <= limit ? hits : new ArrayList<>(hits.subList(0, limit));
    }

    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        for (String raw : normalized.split("[^a-z0-9]+")) {
            if (raw.length() < 2 || STOP.contains(raw)) {
                continue;
            }
            tokens.add(stem(raw));
        }
        return tokens;
    }

    /** Remove só o plural mais comum, para "mercados" achar "mercado" e "operações" achar "operação". */
    private static String stem(String token) {
        if (token.length() > 4 && token.endsWith("oes")) {
            return token.substring(0, token.length() - 3) + "ao";
        }
        if (token.length() > 3 && token.endsWith("s")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }
}
