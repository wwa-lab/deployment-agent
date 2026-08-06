package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.contracts.enums.DirectoryGroupType;
import com.wwa.agenthub.contracts.enums.SdlcStageKey;
import com.wwa.agenthub.domain.resourcecenter.model.DirectoryGroup;

import java.util.List;

public record DirectoryGroupDto(
        String key,
        String title,
        String description,
        DirectoryGroupType type,
        SdlcStageKey stageKey,
        Integer stageOrder,
        String agentName,
        boolean enabled,
        int sortOrder,
        List<DirectoryLinkDto> links
) {
    public static DirectoryGroupDto from(DirectoryGroup group) {
        return new DirectoryGroupDto(
                group.key(),
                group.title(),
                group.description(),
                group.type(),
                group.stageKey(),
                group.stageOrder(),
                group.agentName(),
                group.enabled(),
                group.sortOrder(),
                group.links().stream().map(DirectoryLinkDto::from).toList());
    }
}
