package com.wwa.agenthub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Time source configuration.
 *
 * <p>Exposes a single application-wide {@link Clock} bean so that every
 * service that needs the current instant can ask the clock instead of
 * calling {@code Instant.now()} directly. Tests can override this bean with
 * {@link Clock#fixed} / {@link Clock#offset} to simulate time progression,
 * which is a hard requirement for verifying any SLA-driven or scheduled
 * behavior (future timeout sweeper, retry back-off, etc.).
 *
 * <p>This is infrastructure debt, not an MVP Foundation Seam — services
 * start reading {@code clock.instant()} immediately. The seam exists so
 * that the future {@code expected_sla_minutes} sweeper has a mockable
 * time source without a cross-cutting refactor when it ships.
 *
 * <p>UTC is the canonical storage timezone for durable entity timestamps
 * ({@code Request.createdAt}, {@code Task.startTime}, audit entries, etc.).
 * Display-layer time-zone conversion is the frontend's responsibility.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneOffset.UTC);
    }
}
