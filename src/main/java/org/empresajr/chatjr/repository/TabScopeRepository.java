package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.TabScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TabScopeRepository extends JpaRepository<TabScope, Long> {

    List<TabScope> findByClientId(Long clientId);

    Optional<TabScope> findByClientIdAndTabId(Long clientId, Long tabId);
}
