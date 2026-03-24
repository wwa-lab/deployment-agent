package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.Stage;

public record RequestPurgeResultDto(
        String releaseFlowId,
        String requestId,
        Stage stage,
        boolean releaseFlowDeleted,
        int remainingRequestCount,
        int activeRequestCount
) {}
