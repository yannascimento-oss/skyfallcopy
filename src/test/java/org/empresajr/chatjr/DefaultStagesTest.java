package org.empresajr.chatjr;

import org.empresajr.chatjr.domain.DefaultStages;
import org.empresajr.chatjr.domain.Slugs;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultStagesTest {

    @Test
    void hasFifteenStagesWithUniqueSlugsAndDefinitions() {
        assertEquals(15, DefaultStages.ALL.size());
        Set<String> slugs = DefaultStages.ALL.stream().map(s -> Slugs.slugify(s.name())).collect(Collectors.toSet());
        assertEquals(15, slugs.size());
        DefaultStages.ALL.forEach(s -> {
            assertFalse(s.whatIsIt().isBlank());
            assertFalse(s.objective().isBlank());
        });
    }

    @Test
    void slugsRemoveAccentsAndPunctuation() {
        assertEquals("produto-servico", Slugs.slugify("Produto/Serviço"));
        assertEquals("vpl-tir-e-payback", Slugs.slugify("VPL, TIR e Payback"));
        assertEquals("publico-alvo", Slugs.slugify("Público-Alvo"));
        assertEquals("etapa", Slugs.slugify("???"));
        assertEquals("etapa", Slugs.slugify(null));
    }
}
