package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.ClientAccount;
import org.empresajr.chatjr.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientAccountRepository extends JpaRepository<ClientAccount, Long> {

    Optional<ClientAccount> findByEmail(String email);

    Optional<ClientAccount> findByTokenHash(String tokenHash);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    List<ClientAccount> findByRoleOrderByCompanyAsc(Role role);
}
