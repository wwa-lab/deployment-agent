package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.Stage;

public record RequestArchiveResultDto(
        String releaseFlowId,
        String requestId,
        Stage stage,
        boolean requestArchived,
        boolean releaseFlowArchived,
        int activeRequestCount
) {}
