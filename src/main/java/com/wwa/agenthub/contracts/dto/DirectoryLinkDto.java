package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.contracts.enums.DirectoryLinkIconKey;
import com.wwa.agenthub.contracts.enums.DirectoryLinkKind;
import com.wwa.agenthub.domain.resourcecenter.model.DirectoryLink;

public record DirectoryLinkDto(
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
    public static DirectoryLinkDto from(DirectoryLink link) {
        return new DirectoryLinkDto(
                link.id(),
                link.title(),
                link.description(),
                link.url(),
                link.kind(),
                link.kindLabel(),
                link.iconKey(),
                link.enabled(),
                link.sortOrder());
    }
}
