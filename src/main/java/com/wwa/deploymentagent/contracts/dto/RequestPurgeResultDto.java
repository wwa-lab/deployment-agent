package com.wwa.deploymentagent.contracts.dto;

public record RequestPurgeResultDto(
        String releaseFlowId,
        String requestId,
        String stage,
        boolean releaseFlowDeleted,
        int remainingRequestCount,
        int activeRequestCount
) {}
