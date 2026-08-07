package com.wwa.agenthub.contracts.dto.integration;

import com.wwa.agenthub.contracts.enums.ArtifactKind;
import com.wwa.agenthub.contracts.enums.ArtifactRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ArtifactUploadMetadata(
        @NotNull ArtifactRole role,
        @NotBlank @Size(max = 128) String kind,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String mediaType,
        @Min(0) long sizeBytes,
        @NotNull @Valid Digest digest,
        @Size(max = 1024) String sourcePath
) implements StrictIntegrationRequest {
    public ArtifactUploadMetadata(
            ArtifactRole role,
            ArtifactKind kind,
            String name,
            String mediaType,
            long sizeBytes,
            String sha256,
            String sourcePath
    ) {
        this(role, kind.name(), name, mediaType, sizeBytes, new Digest("SHA-256", sha256), sourcePath);
    }

    public record Digest(
            @NotBlank @Pattern(regexp = "^SHA-256$") String algorithm,
            @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String value
    ) implements StrictIntegrationRequest {
    }
}
