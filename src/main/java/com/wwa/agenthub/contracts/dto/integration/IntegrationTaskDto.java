package com.wwa.agenthub.contracts.dto.integration;

import java.time.Instant;
import java.util.List;

public record IntegrationTaskDto(
        String taskId,
        String workItemId,
        String agentModuleId,
        String title,
        String description,
        String status,
        IntegrationReferences.User assignee,
        IntegrationReferences.Capability capability,
        IntegrationReferences.ProjectContext projectContext,
        List<String> approvedInputArtifactIds,
        String activeExecutionId,
        String latestExecutionId,
        int executionCount,
        Instant createdAt,
        Instant updatedAt,
        IntegrationReferences.TaskActions actions
) {
    public IntegrationTaskDto {
        approvedInputArtifactIds = approvedInputArtifactIds == null
                ? List.of()
                : List.copyOf(approvedInputArtifactIds);
    }
}
