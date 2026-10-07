package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<AuditLog> findByClientIdOrderByCreatedAtDescIdDesc(Long clientId, Pageable pageable);

    Page<AuditLog> findByActionOrderByCreatedAtDescIdDesc(String action, Pageable pageable);

    Page<AuditLog> findByClientIdAndActionOrderByCreatedAtDescIdDesc(Long clientId, String action, Pageable pageable);
}
