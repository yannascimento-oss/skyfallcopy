package org.empresajr.chatjr.web;

import jakarta.validation.Valid;
import org.empresajr.chatjr.domain.AccountPrincipal;
import org.empresajr.chatjr.service.ChatService;
import org.empresajr.chatjr.web.dto.AskRequest;
import org.empresajr.chatjr.web.dto.ChatAnswer;
import org.empresajr.chatjr.web.dto.ConversationView;
import org.empresajr.chatjr.web.dto.MessageView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Chat do cliente com o próprio plano. O acesso é restrito ao perfil CLIENT na SecurityConfig. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    public ChatAnswer ask(@Valid @RequestBody AskRequest body) {
        AccountPrincipal who = PlanController.caller();
        return chat.ask(who, body.conversationId(), body.question());
    }

    @GetMapping("/conversations")
    public List<ConversationView> conversations() {
        return chat.listConversations(PlanController.caller().id());
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageView> messages(@PathVariable Long id) {
        return chat.conversationMessages(PlanController.caller().id(), id);
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        chat.deleteConversation(PlanController.caller().id(), id);
        return ResponseEntity.noContent().build();
    }
}
