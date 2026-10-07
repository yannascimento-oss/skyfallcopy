package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface QueryLogRepository extends JpaRepository<QueryLog, Long> {

    long countByClientIdAndCreatedAtAfter(Long clientId, Instant after);

    List<QueryLog> findByClientIdAndCreatedAtAfter(Long clientId, Instant after);

    List<QueryLog> findByCreatedAtAfter(Instant after);
}
