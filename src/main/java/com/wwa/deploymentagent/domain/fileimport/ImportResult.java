package com.wwa.deploymentagent.domain.fileimport;

public record ImportResult(
        String releaseFlowId,
        String releaseId,
        String stage,
        int taskCount,
        String snowGroup,
        String application,
        String agent
) {}
