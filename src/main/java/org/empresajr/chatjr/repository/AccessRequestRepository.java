package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.AccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    List<AccessRequest> findByHandledAtIsNullOrderByCreatedAtAsc();

    long countByHandledAtIsNull();

    boolean existsByEmailAndHandledAtIsNull(String email);
}
