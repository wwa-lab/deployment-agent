package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.domain.configuration.ConfigurationComponent;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record ConfigurationComponentDto(
        String componentId,
        String systemType,
        String displayName,
        String area,
        String application,
        String snowGroup,
        String agent,
        boolean trackServiceUser,
        boolean trackCredential,
        String serviceEndpoint,
        String serviceUser,
        boolean credentialConfigured,
        String description,
        String updatedBy,
        Instant updatedAt
) {
    public static ConfigurationComponentDto from(ConfigurationComponent component) {
        return new ConfigurationComponentDto(
                component.getComponentId(),
                component.getSystemType(),
                component.getDisplayName(),
                component.getArea(),
                component.getApplication(),
                component.getSnowGroup(),
                component.getAgent(),
                component.isTrackServiceUser(),
                component.isTrackCredential(),
                component.getServiceEndpoint(),
                component.getServiceUser(),
                component.getCredentialValue() != null && !component.getCredentialValue().isBlank(),
                component.getDescription(),
                component.getUpdatedBy(),
                component.getUpdatedAt()
        );
    }

    public record UpsertRequest(
            @NotBlank String componentId,
            @NotBlank String displayName,
            @NotBlank String area,
            @NotBlank String application,
            @NotBlank String snowGroup,
            @NotBlank String agent,
            @NotBlank String serviceEndpoint,
            String serviceUser,
            String credentialValue,
            String description
    ) {}
}
