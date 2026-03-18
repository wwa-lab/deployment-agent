package com.wwa.deploymentagent.contracts.dto;

public record AuthResponseDto(
        String userId,
        String role,
        String displayName
) {}
