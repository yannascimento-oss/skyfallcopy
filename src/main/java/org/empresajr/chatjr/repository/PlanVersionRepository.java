package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.PlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanVersionRepository extends JpaRepository<PlanVersion, Long> {

    List<PlanVersion> findByTabIdOrderByVersionDesc(Long tabId);

    Optional<PlanVersion> findByTabIdAndVersion(Long tabId, int version);
}
