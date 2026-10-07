package org.empresajr.chatjr.domain;

import java.util.List;

/** Etapas padrão de um Plano de Negócios, com a definição genérica de cada uma (sem fatos do cliente). */
public final class DefaultStages {

    public record Stage(String name, String whatIsIt, String objective) {
        public String shortDescription() {
            return whatIsIt;
        }
    }

    public static final List<Stage> ALL = List.of(
            new Stage("Resumo Executivo", "Visão resumida do negócio e das principais conclusões do plano.", "Permitir que qualquer leitor entenda o plano inteiro em poucos minutos."),
            new Stage("Empresa", "Apresentação da empresa: origem, sócios e proposta.", "Contextualizar quem é a empresa e de onde ela parte."),
            new Stage("Produto/Serviço", "Descrição do que a empresa vende e de como entrega.", "Deixar claro o que é ofertado ao cliente."),
            new Stage("Proposta de Valor", "Promessa central que diferencia a empresa para o cliente.", "Explicar por que o cliente escolheria esta empresa."),
            new Stage("Mercado", "Análise do mercado em que a empresa atua.", "Mostrar o tamanho e o comportamento do mercado."),
            new Stage("Público-Alvo", "Descrição de quem são os clientes que a empresa quer atender.", "Orientar comunicação, produto e preço a partir de quem se quer atingir."),
            new Stage("Concorrentes", "Mapa dos concorrentes e de como eles se posicionam.", "Comparar a empresa com as alternativas do cliente."),
            new Stage("Análise SWOT", "Forças, fraquezas, oportunidades e ameaças do negócio.", "Dar uma visão equilibrada dos pontos internos e externos."),
            new Stage("Plano Financeiro", "Projeções de receita, custos e ponto de equilíbrio.", "Mostrar se e quando o negócio se sustenta financeiramente."),
            new Stage("Riscos", "Riscos identificados para o negócio.", "Antecipar o que pode dar errado para poder se preparar."),
            new Stage("Metas", "Metas definidas para o negócio.", "Dar direção e critérios de acompanhamento."),
            new Stage("Estratégia de Marketing", "Plano de comunicação e de atração de clientes.", "Definir como a empresa chega até o público-alvo."),
            new Stage("Investimento Inicial", "Valor e destino do investimento para abrir o negócio.", "Mostrar quanto é preciso investir e em quê."),
            new Stage("VPL, TIR e Payback", "Indicadores de retorno do investimento.", "Avaliar se o investimento compensa e em quanto tempo volta."),
            new Stage("Análise de Sensibilidade", "Como os resultados mudam em cenários diferentes.", "Mostrar a resistência do plano a variações."));

    private DefaultStages() {
    }
}
