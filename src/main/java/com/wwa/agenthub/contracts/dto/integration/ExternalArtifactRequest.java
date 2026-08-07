package com.wwa.agenthub.contracts.dto.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExternalArtifactRequest(
        @NotNull @Valid ArtifactUploadMetadata metadata,
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
        String referenceId
) implements StrictIntegrationRequest {
}
