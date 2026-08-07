package com.wwa.agenthub.contracts.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FailExecutionRequest(
        @NotNull @Valid FailureReason failureReason
) implements StrictIntegrationRequest {
    public FailExecutionRequest(String code, String message, boolean retryable) {
        this(new FailureReason(code, message, retryable));
    }

    public record FailureReason(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,127}$") String code,
            @NotBlank @Size(max = 2000) String message,
            boolean retryable
    ) implements StrictIntegrationRequest {
    }
}
