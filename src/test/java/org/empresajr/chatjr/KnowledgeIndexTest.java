package org.empresajr.chatjr;

import org.empresajr.chatjr.domain.HtmlText;
import org.empresajr.chatjr.domain.IndicatorCalculator;
import org.empresajr.chatjr.domain.KnowledgeIndex;
import org.empresajr.chatjr.domain.QueryClassifier;
import org.empresajr.chatjr.domain.QueryType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeIndexTest {

    private static KnowledgeIndex.Doc doc(long id, String tab, String text) {
        return new KnowledgeIndex.Doc(id, tab, 0, text);
    }

    private final KnowledgeIndex index = new KnowledgeIndex(List.of(
            doc(1, "Mercado", "O mercado de academias cresce dez por cento ao ano no Brasil."),
            doc(2, "Riscos", "O principal risco e a perda de alunos para concorrentes de baixo custo."),
            doc(3, "Plano Financeiro", "A receita prevista e de duzentos mil reais por ano.")));

    @Test
    void ranksTheMostRelevantStageFirst() {
        assertEquals(1L, index.search("Quanto o mercado cresce?", 5).get(0).doc().tabId());
        assertEquals(3L, index.search("Qual a receita prevista?", 5).get(0).doc().tabId());
    }

    @Test
    void matchesPluralsAndIgnoresAccents() {
        assertEquals(2L, index.search("concorrente", 5).get(0).doc().tabId());
        assertEquals(2L, index.search("PERDA de álunos".replace("á", "a"), 5).get(0).doc().tabId());
    }

    @Test
    void returnsNothingWhenNoWordIsShared() {
        assertTrue(index.search("Qual a cor do ceu?", 5).isEmpty());
        assertTrue(index.search("qual o que de", 5).isEmpty());
        assertTrue(new KnowledgeIndex(List.of()).search("mercado", 3).isEmpty());
    }

    @Test
    void rareTermsOutweighCommonOnes() {
        KnowledgeIndex idx = new KnowledgeIndex(List.of(
                doc(1, "A", "preco preco preco do produto"),
                doc(2, "B", "preco da franquia exclusiva"),
                doc(3, "C", "preco geral do mercado")));
        assertEquals(2L, idx.search("preco franquia", 3).get(0).doc().tabId());
        assertEquals(2, index.search("mercado risco receita", 2).size());
    }

    @Test
    void htmlBecomesPlainText() {
        String text = HtmlText.toPlain("<h4>Titulo</h4><p>Linha &amp; um<br>dois</p><ul><li>a</li><li>b</li></ul>"
                + "<table><tr><td>x</td><td>y</td></tr></table>");
        assertTrue(text.contains("Linha & um\ndois") && text.contains("a\nb") && text.contains("x | y"));
        assertTrue(HtmlText.toPlain(null).isEmpty());
    }

    @Test
    void classifiesQuestionsByIntent() {
        assertEquals(QueryType.DECISAO, QueryClassifier.classify("Devo contratar mais um funcionário?"));
        assertEquals(QueryType.DUVIDA, QueryClassifier.classify("Não entendi o que é payback"));
        assertEquals(QueryType.INTERPRETACAO, QueryClassifier.classify("Por que o risco é alto?"));
        assertEquals(QueryType.INFORMACAO, QueryClassifier.classify("Quanto o mercado cresce?"));
        assertEquals(QueryType.DUVIDA, QueryType.parse("xx", QueryType.DUVIDA));
        assertEquals(QueryType.DECISAO, QueryType.parse("decisao", QueryType.INFORMACAO));
    }

    @Test
    void indicatorCalculatorCountsWhatReallyHappened() {
        Instant now = Instant.parse("2026-10-10T12:00:00Z");
        var result = IndicatorCalculator.compute(List.of(
                new IndicatorCalculator.Entry("q1", QueryType.INFORMACAO, true, 1L, "Crescimento", false, now.minusSeconds(3600)),
                new IndicatorCalculator.Entry("q2", QueryType.INFORMACAO, false, null, null, false, now.minusSeconds(7200)),
                new IndicatorCalculator.Entry("q3", QueryType.DECISAO, true, 2L, "Concorrência", true, now.minus(Duration.ofDays(1)))),
                Map.of(1L, "Mercado", 2L, "Riscos"), now, 30);
        assertEquals(3, result.total());
        assertEquals(67, result.answeredPercent());
        assertEquals(1, result.degraded());
        assertEquals(2, result.byType().get("INFORMACAO"));
        assertEquals("Mercado", result.topSources().get(0).label());
        assertEquals("q2", result.unansweredQuestions().get(0).question());
        assertEquals(14, result.perDay().size());
        assertEquals(3, result.perDay().stream().mapToInt(IndicatorCalculator.DayCount::count).sum());

        var empty = IndicatorCalculator.compute(List.of(), Map.of(), now, 7);
        assertEquals(0, empty.total());
        assertEquals(7, empty.perDay().size());
    }
}
