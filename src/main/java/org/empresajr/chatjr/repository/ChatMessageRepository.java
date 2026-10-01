package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    List<ChatMessage> findTop4ByConversationIdOrderByIdDesc(Long conversationId);
}
