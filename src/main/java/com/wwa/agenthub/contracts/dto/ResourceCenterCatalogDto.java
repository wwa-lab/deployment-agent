package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.domain.resourcecenter.ResourceCenterCatalogEntity;
import com.wwa.agenthub.domain.resourcecenter.model.DirectoryScope;

import java.time.Instant;
import java.util.List;

public record ResourceCenterCatalogDto(
        long version,
        String updatedBy,
        Instant updatedAt,
        List<DirectoryScopeDto> scopes
) {
    public static ResourceCenterCatalogDto from(
            ResourceCenterCatalogEntity entity,
            List<DirectoryScope> projectedScopes) {
        return new ResourceCenterCatalogDto(
                entity.getVersion(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt(),
                projectedScopes.stream().map(DirectoryScopeDto::from).toList());
    }
}
