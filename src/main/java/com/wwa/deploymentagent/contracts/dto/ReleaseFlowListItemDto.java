package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.ReviewStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.Request;

import java.util.List;

public record ReleaseFlowListItemDto(
        String id,
        String projectId,
        String projectName,
        String releaseId,
        String normalizedReleaseId,
        Stage currentStage,
        FlowStatus flowStatus,
        ReviewStatus reviewStatus,
        java.time.Instant archivedAt,
        String archivedBy,
        RequestStatus sitStatus,
        RequestStatus uatStatus,
        RequestStatus prodStatus
) {
    public static ReleaseFlowListItemDto from(ReleaseFlow rf, List<Request> requests) {
        return new ReleaseFlowListItemDto(
                rf.getId(),
                rf.getProjectId(),
                rf.getProjectName(),
                rf.getReleaseId(),
                rf.getNormalizedReleaseId(),
                rf.getCurrentStage(),
                rf.getFlowStatus(),
                rf.getReviewStatus(),
                rf.getArchivedAt(),
                rf.getArchivedBy(),
                requestStatusFor(requests, Stage.SIT),
                requestStatusFor(requests, Stage.UAT),
                requestStatusFor(requests, Stage.PROD)
        );
    }

    private static RequestStatus requestStatusFor(List<Request> requests, Stage stage) {
        if (requests == null || requests.isEmpty()) {
            return RequestStatus.Pending;
        }

        return requests.stream()
                .filter(request -> request.getStage() == stage)
                .sorted((left, right) -> {
                    boolean leftArchived = left.getArchivedAt() != null;
                    boolean rightArchived = right.getArchivedAt() != null;
                    return Boolean.compare(leftArchived, rightArchived);
                })
                .map(Request::getRequestStatus)
                .findFirst()
                .orElse(RequestStatus.Pending);
    }
}
