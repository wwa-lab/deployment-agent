package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.ConfigKey;
import com.wwa.deploymentagent.domain.configuration.ConfigurationItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ConfigurationItemDto(
        ConfigKey configKey,
        String configValue,
        String description,
        String updatedBy,
        Instant updatedAt
) {
    public static ConfigurationItemDto from(ConfigurationItem item) {
        return new ConfigurationItemDto(
                item.getConfigKey(),
                item.getConfigValue(),
                item.getDescription(),
                item.getUpdatedBy(),
                item.getUpdatedAt()
        );
    }

    /** Request body for upsert. */
    public record UpsertRequest(
            @NotNull ConfigKey key,
            @NotBlank String value,
            String description
    ) {}
}
