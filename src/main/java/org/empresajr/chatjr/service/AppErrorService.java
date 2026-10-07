package org.empresajr.chatjr.service;

import org.empresajr.chatjr.domain.AppError;
import org.empresajr.chatjr.repository.AppErrorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AppErrorService {

    private final AppErrorRepository repository;
    private final Clock clock;

    public AppErrorService(AppErrorRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Transação própria: o registro do erro não pode ser desfeito junto com a operação que falhou. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String message, String path) {
        try {
            repository.save(new AppError(message == null ? "(sem mensagem)" : message, path, clock.instant()));
        } catch (RuntimeException ignored) {
            // Registrar o erro nunca deve causar outro erro.
        }
    }
}
