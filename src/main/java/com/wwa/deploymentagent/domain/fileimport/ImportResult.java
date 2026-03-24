package com.wwa.deploymentagent.domain.fileimport;

import com.wwa.deploymentagent.contracts.enums.Stage;

public record ImportResult(
        String releaseFlowId,
        String releaseId,
        Stage stage,
        int taskCount,
        String snowGroup,
        String application,
        String agent
) {}
