package com.wwa.agenthub.platform.domain.integration.artifact;

import com.wwa.agenthub.contracts.enums.ArtifactRole;
import com.wwa.agenthub.contracts.enums.ArtifactStorageMode;

import java.time.Instant;

/** Content-free Artifact projection used by list/read-model queries. */
public record IntegrationArtifactMetadata(
        String artifactId,
        String taskId,
        String executionId,
        ArtifactRole role,
        String kind,
        String name,
        String mediaType,
        long sizeBytes,
        String sha256,
        String sourcePath,
        ArtifactStorageMode storageMode,
        String referenceId,
        Instant createdAt
) {
}
