package com.wwa.agenthub.contracts.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.validation.IntegrationResourceIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IntegrationTaskBindingRequest(
        @NotBlank @Pattern(regexp = IntegrationResourceIds.REGEX) String assigneeUserId,
        @NotNull @Valid Capability capability,
        @Valid RepositoryContext repository
) implements StrictIntegrationRequest {
    public record Capability(
            @NotNull CapabilityType capabilityType,
            @NotBlank @Size(max = 255) String capabilityId,
            @NotBlank @Size(max = 128) String capabilityVersion
    ) implements StrictIntegrationRequest {
    }

    public record RepositoryContext(
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
            String repositoryId,
            @NotBlank @Size(max = 2048) String url,
            @NotBlank @Size(max = 128) String provider,
            @NotBlank @Size(max = 1024) String branch,
            @NotBlank
            @Pattern(regexp = "^(?:[a-f0-9]{40}|[a-f0-9]{64})$")
            String commit
    ) implements StrictIntegrationRequest {
    }

    @JsonIgnore
    @AssertTrue(message = "repository is required for non-MANUAL capabilities")
    public boolean isRepositoryBindingValid() {
        return capability == null
                || capability.capabilityType() == CapabilityType.MANUAL
                || repository != null;
    }
}
