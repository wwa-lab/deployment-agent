package com.wwa.agenthub.contracts.dto.integration;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubmitExecutionRequest(
        @Size(max = 10000) String summary,
        @NotNull @Size(max = 1000) List<@jakarta.validation.constraints.NotBlank String> artifactIds
) implements StrictIntegrationRequest {
    public SubmitExecutionRequest {
        artifactIds = artifactIds == null ? null : List.copyOf(artifactIds);
    }
}
