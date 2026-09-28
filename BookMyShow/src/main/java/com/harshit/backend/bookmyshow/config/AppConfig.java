package com.harshit.backend.bookmyshow.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /**
     * Injected everywhere instead of calling {@code LocalDateTime.now()} inline, so tests can
     * advance time to exercise hold expiry without sleeping.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
