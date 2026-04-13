package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.contracts.AccessScope;

import java.util.List;
import java.util.Set;

public record AuthResponseDto(
        String userId,
        String role,
        List<String> roles,
        Set<String> permissions,
        String displayName,
        List<AccessScope> scopes
) {}
