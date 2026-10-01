package org.empresajr.chatjr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AppConfig {

    /** Relógio injetável: facilita testar regras que dependem de horário (bloqueio, validade de convite). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Processamentos de PDF rodam fora da requisição, uma thread virtual por tarefa. */
    @Bean(name = "processingExecutor", destroyMethod = "shutdown")
    public ExecutorService processingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
