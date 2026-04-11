package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.domain.configuration.ConfigurationScope;
import com.wwa.deploymentagent.domain.configuration.ScopeDirectoryEntry;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record ScopeDirectoryEntryDto(
        String id,
        String application,
        String snowGroup,
        String scopeSource,
        String updatedBy,
        Instant updatedAt
) {
    public static ScopeDirectoryEntryDto from(ScopeDirectoryEntry entry) {
        return new ScopeDirectoryEntryDto(
                entry.getId(),
                entry.getApplication(),
                entry.getSnowGroup(),
                new ConfigurationScope(entry.getApplication(), entry.getSnowGroup(), null).scopeSource(),
                entry.getUpdatedBy(),
                entry.getUpdatedAt());
    }

    public record UpsertRequest(
            String id,
            @NotBlank String application,
            String snowGroup
    ) {}
}
