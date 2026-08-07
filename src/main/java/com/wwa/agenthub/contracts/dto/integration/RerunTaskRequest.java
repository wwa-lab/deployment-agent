package com.wwa.agenthub.contracts.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RerunTaskRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
        String executionId
) implements StrictIntegrationRequest {
}
