package com.wwa.agenthub.platform.domain.integration.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Removes only expired in-progress reservations; completed records follow resource lifetime. */
@Service
@RequiredArgsConstructor
public class IdempotencyRetentionService {

    private final IdempotencyRecordRepository repository;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${app.integration.retention-cleanup-delay-ms:3600000}",
            initialDelayString = "${app.integration.retention-cleanup-delay-ms:3600000}")
    @Transactional
    public int purgeExpired() {
        return repository.deleteExpired(clock.instant());
    }
}
