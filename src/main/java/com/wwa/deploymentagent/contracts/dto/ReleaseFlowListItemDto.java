package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.ReviewStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;

public record ReleaseFlowListItemDto(
        String id,
        String projectId,
        String projectName,
        String releaseId,
        String normalizedReleaseId,
        Stage currentStage,
        FlowStatus flowStatus,
        ReviewStatus reviewStatus
) {
    public static ReleaseFlowListItemDto from(ReleaseFlow rf) {
        return new ReleaseFlowListItemDto(
                rf.getId(),
                rf.getProjectId(),
                rf.getProjectName(),
                rf.getReleaseId(),
                rf.getNormalizedReleaseId(),
                rf.getCurrentStage(),
                rf.getFlowStatus(),
                rf.getReviewStatus()
        );
    }
}
