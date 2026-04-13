package com.wwa.agenthub.contracts.dto;

public record RequestArchiveResultDto(
        String releaseFlowId,
        String requestId,
        String stage,
        boolean requestArchived,
        boolean releaseFlowArchived,
        int activeRequestCount
) {}
