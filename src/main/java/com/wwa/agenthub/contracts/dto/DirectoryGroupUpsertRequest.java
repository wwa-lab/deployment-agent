package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.contracts.enums.DirectoryGroupType;
import com.wwa.agenthub.contracts.enums.SdlcStageKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DirectoryGroupUpsertRequest(
        @NotBlank String key,
        @NotBlank String title,
        String description,
        @NotNull DirectoryGroupType type,
        SdlcStageKey stageKey,
        Integer stageOrder,
        String agentName,
        Boolean enabled,
        Integer sortOrder
) {}
