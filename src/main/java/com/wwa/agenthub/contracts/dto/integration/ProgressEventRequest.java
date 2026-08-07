package com.wwa.agenthub.contracts.dto.integration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ProgressEventRequest(
        @NotNull @Positive Long sequenceNumber,
        @Min(0) @Max(100) Integer percent,
        @NotBlank @Size(max = 2000) String message,
        Instant clientTimestamp
) implements StrictIntegrationRequest {
}
