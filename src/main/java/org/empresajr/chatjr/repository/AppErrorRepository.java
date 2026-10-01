package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.AppError;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppErrorRepository extends JpaRepository<AppError, Long> {
}
