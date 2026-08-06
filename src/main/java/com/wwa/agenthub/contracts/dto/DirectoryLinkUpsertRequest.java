package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.contracts.enums.DirectoryLinkKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DirectoryLinkUpsertRequest(
        @NotBlank String title,
        String description,
        @NotBlank String url,
        @NotNull DirectoryLinkKind kind,
        String kindLabel,
        String iconKey,
        Boolean enabled,
        Integer sortOrder,
        String targetScopeKey,
        String targetGroupKey
) {}
