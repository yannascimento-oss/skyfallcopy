package org.empresajr.chatjr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Relógio injetável: facilita testar regras que dependem de horário (bloqueio, validade de convite). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
