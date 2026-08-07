package com.wwa.agenthub.platform.domain.integration.artifact;

import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/** Retains immutable metadata while expiring unheld Artifact BLOB content. */
@Service
@RequiredArgsConstructor
public class ArtifactRetentionService {

    private final ArtifactRetentionBatchService batchService;
    private final IntegrationClientProperties properties;
    private final Clock clock;

    public Instant contentExpiry() {
        return clock.instant().plus(properties.getArtifactContentRetention());
    }

    @Scheduled(
            fixedDelayString = "${app.integration.retention-cleanup-delay-ms:3600000}",
            initialDelayString = "${app.integration.retention-cleanup-delay-ms:3600000}")
    public int purgeExpiredContent() {
        Instant now = clock.instant();
        int batchSize = Math.max(1, Math.min(
                1000,
                properties.getArtifactRetentionCleanupBatchSize()));
        int maxBatches = Math.max(1, Math.min(
                100,
                properties.getArtifactRetentionCleanupMaxBatchesPerRun()));
        int totalPurged = 0;
        for (int index = 0; index < maxBatches; index++) {
            ArtifactRetentionBatchService.BatchResult result = batchService.purgeBatch(now, batchSize);
            totalPurged += result.purged();
            if (result.selected() < batchSize) {
                break;
            }
        }
        return totalPurged;
    }
}
