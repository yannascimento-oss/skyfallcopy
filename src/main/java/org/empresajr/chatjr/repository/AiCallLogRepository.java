package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.AiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    long countByClientIdAndKindAndCreatedAtAfter(Long clientId, String kind, Instant after);

    long countByCreatedAtAfter(Instant after);

    long countByStatusAndCreatedAtAfter(String status, Instant after);

    @Query("select coalesce(sum(c.estCostMicroUsd), 0L) from AiCallLog c where c.createdAt > :after")
    long costSince(@Param("after") Instant after);

    @Query("select coalesce(sum(c.inputTokens), 0L) from AiCallLog c where c.createdAt > :after")
    long inputTokensSince(@Param("after") Instant after);

    @Query("select coalesce(sum(c.outputTokens), 0L) from AiCallLog c where c.createdAt > :after")
    long outputTokensSince(@Param("after") Instant after);

    List<AiCallLog> findTop20ByOrderByIdDesc();
}
