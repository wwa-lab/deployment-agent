package com.wwa.agenthub.contracts.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record AgentContributionDashboardStatusDto(
        Map<String, String> statuses,
        String updatedBy,
        Instant updatedAt
) {
    public record UpsertRequest(
            @NotNull Map<String, String> statuses
    ) {}
}
