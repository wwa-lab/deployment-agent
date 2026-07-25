package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.contracts.enums.DirectoryScopeLayout;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DirectoryScopeUpsertRequest(
        @NotBlank String key,
        @NotBlank String title,
        String description,
        @NotNull DirectoryScopeLayout layout,
        Boolean enabled,
        Integer sortOrder
) {}
