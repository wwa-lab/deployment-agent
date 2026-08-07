package com.wwa.agenthub.contracts.dto.integration;

import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.ArtifactStorageMode;
import com.wwa.agenthub.contracts.enums.IntegrationClientType;

public final class IntegrationReferences {

    private IntegrationReferences() {
    }

    public record Capability(
            String capabilityId,
            CapabilityType capabilityType,
            String capabilityVersion,
            String skillId
    ) {
    }

    public record ProjectReference(String projectId, String name) {
    }

    public record RepositoryReference(
            String repositoryId,
            String url,
            String provider
    ) {
    }

    public record ProjectContext(
            String projectContextId,
            ProjectReference project,
            RepositoryReference repository,
            String branch,
            String commit,
            String team,
            String agentModuleId
    ) {
    }

    public record User(String userId, String displayName) {
    }

    public record Client(
            String applicationId,
            IntegrationClientType clientType,
            String clientVersion
    ) {
    }

    public record Digest(String algorithm, String value) {
    }

    public record ArtifactContent(ArtifactStorageMode mode, String referenceId) {
    }

    public record FailureReason(String code, String message, boolean retryable) {
    }

    public record TaskActions(boolean start, boolean review, boolean rerun) {
    }
}
