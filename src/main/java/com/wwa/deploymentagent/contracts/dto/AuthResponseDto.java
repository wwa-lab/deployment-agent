package com.wwa.deploymentagent.contracts.dto;

import java.util.List;
import java.util.Set;

public record AuthResponseDto(
        String userId,
        String role,
        List<String> roles,
        Set<String> permissions,
        String displayName
) {}
