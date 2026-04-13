package com.wwa.agenthub.contracts.dto;

public record RequestRundownUpdateDto(
        String snowGroup,
        String application,
        String agent,
        String owner,
        String site,
        Integer estimatedRemainingMinutes
) {}
