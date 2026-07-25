package com.wwa.agenthub.domain.resourcecenter.model;

import com.wwa.agenthub.contracts.enums.DirectoryScopeLayout;

import java.util.ArrayList;
import java.util.List;

/** Embedded scope value inside the catalog payload. */
public record DirectoryScope(
        String key,
        String title,
        String description,
        DirectoryScopeLayout layout,
        boolean system,
        boolean enabled,
        int sortOrder,
        List<DirectoryGroup> groups
) {
    public DirectoryScope {
        if (description == null) {
            description = "";
        }
        if (groups == null) {
            groups = new ArrayList<>();
        } else {
            groups = new ArrayList<>(groups);
        }
    }

    public DirectoryScope withGroups(List<DirectoryGroup> newGroups) {
        return new DirectoryScope(key, title, description, layout, system, enabled, sortOrder, newGroups);
    }

    public DirectoryScope withEnabled(boolean newEnabled) {
        return new DirectoryScope(key, title, description, layout, system, newEnabled, sortOrder, groups);
    }
}
