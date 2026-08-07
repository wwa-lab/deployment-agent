package com.wwa.agenthub.contracts.dto.integration;

import java.time.Instant;

public record IntegrationProgressEventDto(
        String progressEventId,
        String executionId,
        long sequenceNumber,
        Integer percent,
        String message,
        Instant clientTimestamp,
        Instant recordedAt
) {
}
