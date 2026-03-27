package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.ConfigKey;
import com.wwa.deploymentagent.domain.configuration.ConfigurationItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ConfigurationItemDto(
        String componentInstanceId,
        String componentId,
        ConfigKey configKey,
        String configValue,
        String description,
        String updatedBy,
        Instant updatedAt,
        String application,
        String snowGroup,
        String agent,
        String area,
        String integration,
        String scopeSource,
        boolean sensitive,
        boolean configured
) {
    public static ConfigurationItemDto from(ConfigurationItem item) {
        return new ConfigurationItemDto(
                null,
                null,
                item.getConfigKey(),
                item.getConfigValue(),
                item.getDescription(),
                item.getUpdatedBy(),
                item.getUpdatedAt(),
                null,
                null,
                null,
                null,
                null,
                null,
                item.getConfigKey().isSensitive(),
                item.getConfigValue() != null && !item.getConfigValue().isBlank()
        );
    }

    /** Request body for upsert. */
    public record UpsertRequest(
            @NotNull ConfigKey key,
            @NotBlank String value,
            String description,
            String componentId,
            String componentInstanceId
    ) {}
}
