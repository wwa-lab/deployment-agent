package com.wwa.agenthub.contracts.dto.integration;

import com.wwa.agenthub.contracts.enums.IntegrationExecutionStatus;

import java.time.Instant;

public record IntegrationExecutionDto(
        String executionId,
        String taskId,
        int attemptNumber,
        IntegrationReferences.User user,
        IntegrationReferences.Client client,
        IntegrationReferences.Capability capability,
        IntegrationReferences.ProjectContext projectContext,
        Instant startedAt,
        Instant completedAt,
        IntegrationExecutionStatus status,
        Long durationMs,
        int artifactCount,
        IntegrationReferences.FailureReason failureReason,
        String cancellationReason,
        boolean pendingSync,
        String correlationId
) {
}
