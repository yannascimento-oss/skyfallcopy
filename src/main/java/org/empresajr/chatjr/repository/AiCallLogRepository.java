package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.AiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    long countByClientIdAndKindAndCreatedAtAfter(Long clientId, String kind, Instant after);
}
