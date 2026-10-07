package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByClientIdOrderByUpdatedAtDescIdDesc(Long clientId);

    Optional<Conversation> findByIdAndClientId(Long id, Long clientId);
}
