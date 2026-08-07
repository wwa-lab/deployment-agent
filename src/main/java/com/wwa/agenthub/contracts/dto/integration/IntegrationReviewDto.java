package com.wwa.agenthub.contracts.dto.integration;

import com.wwa.agenthub.contracts.enums.IntegrationReviewDecisionType;

import java.time.Instant;

public record IntegrationReviewDto(
        String reviewDecisionId,
        String taskId,
        String executionId,
        IntegrationReviewDecisionType decision,
        IntegrationReferences.User reviewer,
        String comment,
        Instant decidedAt,
        String correlationId
) {
}
