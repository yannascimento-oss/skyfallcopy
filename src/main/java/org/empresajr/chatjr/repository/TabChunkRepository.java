package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.TabChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TabChunkRepository extends JpaRepository<TabChunk, Long> {

    List<TabChunk> findByTabIdInOrderByTabIdAscChunkIndexAsc(Collection<Long> tabIds);

    long countByTabId(Long tabId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TabChunk c where c.tabId = :tabId")
    int deleteAllByTabId(@Param("tabId") Long tabId);
}
