package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.domain.configuration.ConfigurationComponent;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record ConfigurationComponentDto(
        String componentInstanceId,
        String componentId,
        String systemType,
        String displayName,
        String area,
        String application,
        String snowGroup,
        String agent,
        String scopeSource,
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
                component.getId(),
                component.getComponentId(),
                component.getSystemType(),
                component.getDisplayName(),
                component.getArea(),
                component.getApplication(),
                component.getSnowGroup(),
                component.getAgent(),
                new com.wwa.agenthub.domain.configuration.ConfigurationScope(
                        component.getApplication(),
                        component.getSnowGroup(),
                        component.getAgent()
                ).scopeSource(),
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
            String componentInstanceId,
            @NotBlank String componentId,
            @NotBlank String displayName,
            @NotBlank String area,
            String application,
            String snowGroup,
            String agent,
            @NotBlank String serviceEndpoint,
            String serviceUser,
            String credentialValue,
            String description
    ) {}
}
