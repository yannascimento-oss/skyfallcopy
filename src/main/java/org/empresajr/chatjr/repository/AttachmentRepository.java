package org.empresajr.chatjr.repository;

import jakarta.persistence.LockModeType;
import org.empresajr.chatjr.domain.Attachment;
import org.empresajr.chatjr.domain.AttachmentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Optional<Attachment> findByTabId(Long tabId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attachment a where a.tabId = :tabId")
    Optional<Attachment> findForUpdateByTabId(@Param("tabId") Long tabId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Attachment a set a.state = :failed, a.errorMessage = :message, a.updatedAt = :now "
            + "where a.state = :processing")
    int failInterrupted(@Param("failed") AttachmentState failed, @Param("processing") AttachmentState processing,
                        @Param("message") String message, @Param("now") Instant now);

    @Query("select coalesce(sum(a.storedBytes), 0) from Attachment a")
    long totalStoredBytes();
}
