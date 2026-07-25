package com.wwa.agenthub.domain.resourcecenter.model;

import com.wwa.agenthub.contracts.enums.DirectoryLinkIconKey;
import com.wwa.agenthub.contracts.enums.DirectoryLinkKind;

import java.util.ArrayList;
import java.util.List;

/** Embedded link value inside a directory group payload. */
public record DirectoryLink(
        String id,
        String title,
        String description,
        String url,
        DirectoryLinkKind kind,
        String kindLabel,
        DirectoryLinkIconKey iconKey,
        boolean enabled,
        int sortOrder
) {
    public DirectoryLink {
        if (description == null) {
            description = "";
        }
        if (kindLabel == null) {
            kindLabel = defaultKindLabel(kind);
        }
    }

    public static String defaultKindLabel(DirectoryLinkKind kind) {
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case docs -> "Guideline";
            case tool -> "Tool";
            case workspace -> "Workspace";
            case repo -> "GitHub / source";
        };
    }

    public DirectoryLink withSortOrder(int newSortOrder) {
        return new DirectoryLink(id, title, description, url, kind, kindLabel, iconKey, enabled, newSortOrder);
    }

    public DirectoryLink withEnabled(boolean newEnabled) {
        return new DirectoryLink(id, title, description, url, kind, kindLabel, iconKey, newEnabled, sortOrder);
    }
}
