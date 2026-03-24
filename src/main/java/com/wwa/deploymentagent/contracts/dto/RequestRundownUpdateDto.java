package com.wwa.deploymentagent.contracts.dto;

public record RequestRundownUpdateDto(
        String snowGroup,
        String application,
        String agent,
        String owner,
        String site,
        Integer estimatedRemainingMinutes
) {}
