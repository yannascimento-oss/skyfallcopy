package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.PlanTab;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanTabRepository extends JpaRepository<PlanTab, Long> {

    List<PlanTab> findByClientIdOrderBySortOrderAscIdAsc(Long clientId);

    Optional<PlanTab> findByIdAndClientId(Long id, Long clientId);

    boolean existsByClientIdAndSlug(Long clientId, String slug);

    long countByClientId(Long clientId);
}
