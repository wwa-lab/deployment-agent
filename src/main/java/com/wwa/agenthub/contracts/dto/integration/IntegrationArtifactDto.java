package com.wwa.agenthub.contracts.dto.integration;

import com.wwa.agenthub.contracts.enums.ArtifactRole;

import java.time.Instant;

public record IntegrationArtifactDto(
        String artifactId,
        String workItemId,
        String taskId,
        String executionId,
        ArtifactRole role,
        String kind,
        String name,
        String mediaType,
        long sizeBytes,
        IntegrationReferences.Digest digest,
        IntegrationReferences.ArtifactContent content,
        String sourcePath,
        Instant createdAt
) {
}
