package com.wwa.deploymentagent.contracts.dto;

public record RequestRundownUpdateDto(
        String snowGroup,
        String application,
        String site,
        Integer estimatedRemainingMinutes
) {}
