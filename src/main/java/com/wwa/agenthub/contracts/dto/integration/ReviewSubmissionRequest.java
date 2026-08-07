package com.wwa.agenthub.contracts.dto.integration;

import com.wwa.agenthub.contracts.enums.IntegrationReviewDecisionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewSubmissionRequest(
        @NotNull IntegrationReviewDecisionType decision,
        @Size(max = 2000) String comment
) implements StrictIntegrationRequest {
}
