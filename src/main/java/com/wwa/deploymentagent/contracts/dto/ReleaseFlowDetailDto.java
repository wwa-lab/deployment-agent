package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.ReviewStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;

import java.util.List;

public record ReleaseFlowDetailDto(
        String id,
        String projectId,
        String projectName,
        String releaseId,
        String normalizedReleaseId,
        Stage currentStage,
        FlowStatus flowStatus,
        ReviewStatus reviewStatus,
        List<RequestDto> requests
) {}
