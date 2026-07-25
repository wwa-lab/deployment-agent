package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.contracts.enums.DirectoryScopeLayout;
import com.wwa.agenthub.domain.resourcecenter.model.DirectoryScope;

import java.util.List;

public record DirectoryScopeDto(
        String key,
        String title,
        String description,
        DirectoryScopeLayout layout,
        boolean system,
        boolean enabled,
        int sortOrder,
        List<DirectoryGroupDto> groups
) {
    public static DirectoryScopeDto from(DirectoryScope scope) {
        return new DirectoryScopeDto(
                scope.key(),
                scope.title(),
                scope.description(),
                scope.layout(),
                scope.system(),
                scope.enabled(),
                scope.sortOrder(),
                scope.groups().stream().map(DirectoryGroupDto::from).toList());
    }
}
