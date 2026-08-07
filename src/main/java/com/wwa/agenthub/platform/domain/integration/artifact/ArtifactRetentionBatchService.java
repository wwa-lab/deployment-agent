package com.wwa.agenthub.platform.domain.integration.artifact;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Executes one bounded retention batch in its own transaction. */
@Service
@RequiredArgsConstructor
public class ArtifactRetentionBatchService {

    private final IntegrationArtifactRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult purgeBatch(Instant now, int batchSize) {
        List<String> artifactIds = repository.findExpiredContentIds(
                now,
                PageRequest.of(0, batchSize));
        int purged = 0;
        for (String artifactId : artifactIds) {
            IntegrationArtifact artifact = repository.findByIdForSubmissionUpdate(artifactId)
                    .orElse(null);
            if (artifact != null
                    && artifact.getContent() != null
                    && !artifact.isLegalHold()
                    && artifact.getContentExpiresAt() != null
                    && !artifact.getContentExpiresAt().isAfter(now)) {
                artifact.setContent(null);
                artifact.setContentPurgedAt(now);
                purged++;
            }
        }
        return new BatchResult(artifactIds.size(), purged);
    }

    public record BatchResult(int selected, int purged) {
    }
}
