package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.ReviewStatus;
import java.util.List;

public record ReleaseFlowDetailDto(
        String id,
        String projectId,
        String projectName,
        String releaseId,
        String normalizedReleaseId,
        String currentStage,
        FlowStatus flowStatus,
        ReviewStatus reviewStatus,
        java.time.Instant archivedAt,
        String archivedBy,
        boolean stitched,
        int linkedReleaseCount,
        List<String> linkedReleaseIds,
        List<RequestDto> requests
) {}
