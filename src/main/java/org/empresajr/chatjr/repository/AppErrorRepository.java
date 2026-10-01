package org.empresajr.chatjr.repository;

import org.empresajr.chatjr.domain.AppError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppErrorRepository extends JpaRepository<AppError, Long> {

    List<AppError> findTop20ByOrderByIdDesc();
}
