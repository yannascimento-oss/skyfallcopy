package org.empresajr.chatjr.service;

import org.empresajr.chatjr.ai.AiClient;
import org.empresajr.chatjr.ai.AiException;
import org.empresajr.chatjr.ai.AiRequest;
import org.empresajr.chatjr.ai.AiResult;
import org.empresajr.chatjr.ai.ChatAnswerParser;
import org.empresajr.chatjr.domain.AccountPrincipal;
import org.empresajr.chatjr.domain.AiCallLog;
import org.empresajr.chatjr.domain.ChatMessage;
import org.empresajr.chatjr.domain.Conversation;
import org.empresajr.chatjr.domain.HtmlSanitizer;
import org.empresajr.chatjr.domain.HtmlText;
import org.empresajr.chatjr.domain.KnowledgeIndex;
import org.empresajr.chatjr.domain.MessageRole;
import org.empresajr.chatjr.domain.PlanTab;
import org.empresajr.chatjr.domain.PromptBuilder;
import org.empresajr.chatjr.domain.QueryClassifier;
import org.empresajr.chatjr.domain.QueryLog;
import org.empresajr.chatjr.domain.QueryType;
import org.empresajr.chatjr.domain.TabChunk;
import org.empresajr.chatjr.domain.TextChunker;
import org.empresajr.chatjr.repository.AiCallLogRepository;
import org.empresajr.chatjr.repository.ChatMessageRepository;
import org.empresajr.chatjr.repository.ConversationRepository;
import org.empresajr.chatjr.repository.QueryLogRepository;
import org.empresajr.chatjr.repository.TabChunkRepository;
import org.empresajr.chatjr.web.ApiException;
import org.empresajr.chatjr.web.dto.ChatAnswer;
import org.empresajr.chatjr.web.dto.ConversationView;
import org.empresajr.chatjr.web.dto.MessageView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chat com o plano. A IA só enxerga trechos das abas que o cliente pode ler (publicadas e liberadas para ele) e só
 * é chamada quando a busca acha algo relacionado à pergunta. Sem chave, ou se a IA falhar, o cliente recebe os
 * trechos mais relevantes do próprio plano.
 */
@Service
public class ChatService {

    static final String NOT_IN_PLAN = "<p>Não encontrei essa informação no seu plano. "
            + "Você pode reformular a pergunta ou falar com a consultoria da Empresa JR.</p>";
    static final String NOTHING_RELEASED = "<p>Ainda não há conteúdo liberado no seu plano para eu consultar. "
            + "Assim que a consultoria publicar as etapas, você poderá fazer perguntas aqui.</p>";

    private static final int TOP_K = 6;
    private static final int FALLBACK_EXCERPTS = 3;
    private static final int MAX_HISTORY_CHARS = 400;

    private final PlanService plans;
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final QueryLogRepository queryLogs;
    private final TabChunkRepository tabChunks;
    private final AiCallLogRepository callLogs;
    private final AiClient ai;
    private final SettingsService settings;
    private final LimitService limits;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ChatService(PlanService plans, ConversationRepository conversations, ChatMessageRepository messages,
                       QueryLogRepository queryLogs, TabChunkRepository tabChunks, AiCallLogRepository callLogs, AiClient ai,
                       SettingsService settings, LimitService limits, TransactionTemplate tx, Clock clock) {
        this.plans = plans;
        this.conversations = conversations;
        this.messages = messages;
        this.queryLogs = queryLogs;
        this.tabChunks = tabChunks;
        this.callLogs = callLogs;
        this.ai = ai;
        this.settings = settings;
        this.limits = limits;
        this.tx = tx;
        this.clock = clock;
    }

    private record Turn(boolean user, String text) {
    }

    private record Prepared(List<PlanTab> tabs, Map<Long, List<String>> pdfChunks, List<Turn> history) {
    }

    private record Outcome(String html, String source, Long sourceTabId, boolean inference, boolean degraded,
                           boolean answered, QueryType type, String theme) {
    }

    public ChatAnswer ask(AccountPrincipal who, Long conversationId, String rawQuestion) {
        String question = rawQuestion == null ? "" : rawQuestion.replaceAll("\\s+", " ").trim();
        if (question.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Digite sua pergunta.");
        }
        if (question.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A pergunta pode ter no máximo 500 caracteres.");
        }
        Long clientId = who.id();

        Prepared prepared = tx.execute(status -> {
            limits.checkQuestion(clientId);
            List<Turn> history = new ArrayList<>();
            if (conversationId != null) {
                conversations.findByIdAndClientId(conversationId, clientId).orElseThrow(this::conversationNotFound);
                List<ChatMessage> last = new ArrayList<>(messages.findTop4ByConversationIdOrderByIdDesc(conversationId));
                java.util.Collections.reverse(last);
                for (ChatMessage m : last) {
                    String text = HtmlText.toPlain(m.getHtml());
                    history.add(new Turn(m.getRole() == MessageRole.USER,
                            text.length() > MAX_HISTORY_CHARS ? text.substring(0, MAX_HISTORY_CHARS) : text));
                }
            }
            List<PlanTab> visible = plans.visibleTabs(clientId);
            Map<Long, List<String>> pdfChunks = new HashMap<>();
            if (!visible.isEmpty()) {
                for (TabChunk c : tabChunks.findByTabIdInOrderByTabIdAscChunkIndexAsc(
                        visible.stream().map(PlanTab::getId).toList())) {
                    pdfChunks.computeIfAbsent(c.getTabId(), k -> new ArrayList<>()).add(c.getContent());
                }
            }
            return new Prepared(visible, pdfChunks, history);
        });

        Outcome outcome = decide(clientId, question, prepared);

        return tx.execute(status -> persist(clientId, conversationId, question, outcome));
    }

    // ---------- decisão ----------

    private Outcome decide(Long clientId, String question, Prepared prepared) {
        QueryType guessed = QueryClassifier.classify(question);
        if (prepared.tabs().isEmpty()) {
            return new Outcome(NOTHING_RELEASED, null, null, false, false, false, guessed, null);
        }
        KnowledgeIndex index = buildIndex(prepared.tabs(), prepared.pdfChunks());
        List<KnowledgeIndex.Hit> hits = index.search(searchQuery(question, prepared.history()), TOP_K);
        if (hits.isEmpty()) {
            return new Outcome(NOT_IN_PLAN, null, null, false, false, false, guessed, null);
        }
        Optional<String> key = settings.aiKey();
        if (key.isEmpty()) {
            return extractive(hits, guessed);
        }

        String model = settings.aiModel();
        List<PromptBuilder.Excerpt> excerpts = hits.stream()
                .map(h -> new PromptBuilder.Excerpt(h.doc().tabName(), h.doc().text())).toList();
        List<String> history = prepared.history().stream()
                .map(t -> (t.user() ? "Cliente: " : "Assistente: ") + t.text()).toList();
        AiRequest request = new AiRequest(key.get(), model, PromptBuilder.chatSystem(),
                PromptBuilder.chatUser(question, excerpts, history),
                Math.min(2000, settings.aiMaxTokens()), settings.aiTemperature());
        long started = System.nanoTime();
        try {
            AiResult result = ai.complete(request);
            int ms = (int) ((System.nanoTime() - started) / 1_000_000L);
            Optional<ChatAnswerParser.Parsed> parsed = ChatAnswerParser.parse(result.text());
            if (parsed.isEmpty()) {
                logCall(clientId, model, "FALLBACK", 200, ms, result, "resposta fora do formato");
                return extractive(hits, guessed);
            }
            ChatAnswerParser.Parsed answer = parsed.get();
            QueryType type = QueryType.parse(answer.queryType(), guessed);
            if (!answer.answerable()) {
                logCall(clientId, model, "OK", 200, ms, result, null);
                String html = HtmlSanitizer.sanitize(answer.html());
                return new Outcome(html.isBlank() ? NOT_IN_PLAN : html, null, null, false, false, false, type, answer.theme());
            }
            String html = HtmlSanitizer.sanitize(answer.html());
            if (html.isBlank()) {
                logCall(clientId, model, "FALLBACK", 200, ms, result, "resposta vazia após limpeza");
                return extractive(hits, guessed);
            }
            logCall(clientId, model, "OK", 200, ms, result, null);
            PlanTab source = matchTab(prepared.tabs(), answer.source());
            String theme = answer.theme() != null ? answer.theme() : (source == null ? null : source.getName());
            return new Outcome(html, source == null ? null : source.getName(), source == null ? null : source.getId(),
                    answer.inference(), false, true, type, theme);
        } catch (AiException e) {
            logCall(clientId, model, "ERROR", e.getHttpStatus(), (int) ((System.nanoTime() - started) / 1_000_000L), null, e.getMessage());
            return extractive(hits, guessed);
        }
    }

    /**
     * O PDF é o entregável final, então é ele que a IA interpreta. Aba sem PDF (escrita à mão) usa o próprio texto.
     * Só entram abas que o cliente pode ler.
     */
    private static KnowledgeIndex buildIndex(List<PlanTab> tabs, Map<Long, List<String>> pdfChunks) {
        List<KnowledgeIndex.Doc> docs = new ArrayList<>();
        for (PlanTab tab : tabs) {
            List<String> parts = pdfChunks.getOrDefault(tab.getId(), List.of());
            if (parts.isEmpty()) {
                parts = TextChunker.chunk(HtmlText.toPlain(tab.getHtml()));
            }
            int i = 0;
            for (String part : parts) {
                docs.add(new KnowledgeIndex.Doc(tab.getId(), tab.getName(), i++, part));
            }
        }
        return new KnowledgeIndex(docs);
    }

    /** Perguntas curtas de acompanhamento ("e o segundo?") herdam as palavras da pergunta anterior. */
    private static String searchQuery(String question, List<Turn> history) {
        if (question.split("\\s+").length >= 5) {
            return question;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).user()) {
                return history.get(i).text() + " " + question;
            }
        }
        return question;
    }

    private static PlanTab matchTab(List<PlanTab> tabs, String name) {
        if (name == null) {
            return null;
        }
        return tabs.stream().filter(t -> t.getName().equalsIgnoreCase(name.trim())).findFirst().orElse(null);
    }

    /** Plano B: os trechos do próprio plano que mais se aproximam da pergunta. */
    private Outcome extractive(List<KnowledgeIndex.Hit> hits, QueryType type) {
        StringBuilder html = new StringBuilder("<p>Não consegui usar a inteligência artificial agora, "
                + "mas estes trechos do seu plano parecem responder à sua pergunta:</p>");
        for (KnowledgeIndex.Hit hit : hits.stream().limit(FALLBACK_EXCERPTS).toList()) {
            String text = hit.doc().text().replaceAll("\\s+", " ");
            if (text.length() > 350) {
                text = text.substring(0, 350) + "…";
            }
            html.append("<p><b>").append(HtmlSanitizer.sanitize(hit.doc().tabName())).append("</b>: ")
                    .append(HtmlSanitizer.sanitize(text)).append("</p>");
        }
        KnowledgeIndex.Doc top = hits.get(0).doc();
        return new Outcome(html.toString(), top.tabName(), top.tabId(), false, true, true, type, top.tabName());
    }

    private void logCall(Long clientId, String model, String status, int httpStatus, int ms, AiResult result, String error) {
        Integer in = result == null ? null : result.inputTokens();
        Integer out = result == null ? null : result.outputTokens();
        callLogs.save(new AiCallLog(clientId, "CHAT", model, status, httpStatus, ms, in, out,
                result == null ? null : in * 3L + out * 15L, error, clock.instant()));
    }

    // ---------- gravação ----------

    private ChatAnswer persist(Long clientId, Long conversationId, String question, Outcome o) {
        var now = clock.instant();
        Conversation conversation = conversationId == null
                ? conversations.save(new Conversation(clientId, question.length() > 60 ? question.substring(0, 60) : question, now))
                : conversations.findByIdAndClientId(conversationId, clientId).orElseThrow(this::conversationNotFound);
        messages.save(new ChatMessage(conversation.getId(), MessageRole.USER, HtmlSanitizer.sanitize(question),
                null, false, false, now));
        ChatMessage reply = messages.save(new ChatMessage(conversation.getId(), MessageRole.AI, o.html(), o.source(),
                o.inference(), o.degraded(), now));
        conversation.touch(now);
        conversations.save(conversation);
        queryLogs.save(new QueryLog(clientId, conversation.getId(), question, o.type(), o.answered(), o.sourceTabId(),
                o.theme(), o.degraded(), now));
        return new ChatAnswer(conversation.getId(), reply.getId(), o.html(), o.source(), o.sourceTabId(),
                o.inference(), o.degraded(), o.answered(), now);
    }

    // ---------- conversas ----------

    @Transactional(readOnly = true)
    public List<ConversationView> listConversations(Long clientId) {
        return conversations.findByClientIdOrderByUpdatedAtDescIdDesc(clientId).stream().map(ConversationView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageView> conversationMessages(Long clientId, Long conversationId) {
        conversations.findByIdAndClientId(conversationId, clientId).orElseThrow(this::conversationNotFound);
        return messages.findByConversationIdOrderByIdAsc(conversationId).stream().map(MessageView::of).toList();
    }

    @Transactional
    public void deleteConversation(Long clientId, Long conversationId) {
        Conversation conversation = conversations.findByIdAndClientId(conversationId, clientId)
                .orElseThrow(this::conversationNotFound);
        conversations.delete(conversation);
    }

    private ApiException conversationNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Conversa não encontrada.");
    }
}
