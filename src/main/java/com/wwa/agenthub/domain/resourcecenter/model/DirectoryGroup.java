package com.wwa.agenthub.domain.resourcecenter.model;

import com.wwa.agenthub.contracts.enums.DirectoryGroupType;
import com.wwa.agenthub.contracts.enums.DirectoryLinkKind;
import com.wwa.agenthub.contracts.enums.SdlcStageKey;

import java.util.ArrayList;
import java.util.List;

/** Embedded group value inside a directory scope payload. */
public record DirectoryGroup(
        String key,
        String title,
        String description,
        DirectoryGroupType type,
        SdlcStageKey stageKey,
        Integer stageOrder,
        String agentName,
        boolean enabled,
        int sortOrder,
        List<DirectoryLink> links
) {
    public DirectoryGroup {
        if (description == null) {
            description = "";
        }
        if (agentName == null) {
            agentName = "";
        }
        if (links == null) {
            links = new ArrayList<>();
        } else {
            links = new ArrayList<>(links);
        }
    }

    public DirectoryGroup withLinks(List<DirectoryLink> newLinks) {
        return new DirectoryGroup(
                key, title, description, type, stageKey, stageOrder, agentName, enabled, sortOrder, newLinks);
    }

    public DirectoryGroup withEnabled(boolean newEnabled) {
        return new DirectoryGroup(
                key, title, description, type, stageKey, stageOrder, agentName, newEnabled, sortOrder, links);
    }
}
