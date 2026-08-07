package com.wwa.agenthub.contracts.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelExecutionRequest(
        @NotBlank @Size(max = 2000) String reason
) implements StrictIntegrationRequest {
}
