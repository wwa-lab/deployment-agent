package com.wwa.agenthub.contracts.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StartExecutionRequest(
        @NotBlank @Size(max = 128) String clientVersion,
        @NotNull @Valid Capability capability,
        @NotNull @Valid ProjectContext projectContext
) implements StrictIntegrationRequest {
    @JsonIgnore
    @AssertTrue(message = "repositoryId, branch, and commit must be supplied together and are required outside MANUAL")
    public boolean isRepositoryContextValid() {
        if (capability == null || projectContext == null) {
            return true;
        }
        boolean repository = notBlank(projectContext.repositoryId());
        boolean branch = notBlank(projectContext.branch());
        boolean commit = notBlank(projectContext.commit());
        boolean complete = repository && branch && commit;
        boolean empty = !repository && !branch && !commit;
        return capability.capabilityType() == CapabilityType.MANUAL
                ? complete || empty
                : complete;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public record Capability(
            @NotBlank @Size(max = 255) String capabilityId,
            @NotNull CapabilityType capabilityType,
            @NotBlank @Size(max = 128) String capabilityVersion
    ) implements StrictIntegrationRequest {
    }

    public record ProjectContext(
            @NotBlank @Size(max = 128) String projectId,
            @Size(max = 128)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
            String repositoryId,
            @Size(max = 1024) String branch,
            @Pattern(regexp = "^(?:[a-f0-9]{40}|[a-f0-9]{64})$",
                    message = "commit must be a full lowercase Git SHA")
            String commit
    ) implements StrictIntegrationRequest {
    }
}
